package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.Interview;
import com.group1.career.model.entity.InterviewMessage;
import com.group1.career.model.entity.InterviewQuestion;
import com.group1.career.service.AiService;
import com.group1.career.service.FileService;
import com.group1.career.service.InterviewService;
import com.group1.career.service.QuestionBankService;
import com.group1.career.service.VoiceService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Interview chat lifecycle. Every endpoint resolves the caller via JWT
 * (SecurityUtil) and enforces ownership through InterviewService.assertOwnership.
 * The userId is never trusted from the request body.
 */
@Slf4j
@Tag(name = "Interview API")
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final AiService aiService;
    private final VoiceService voiceService;
    private final FileService fileService;
    private final QuestionBankService questionBankService;

    /** Presigned-URL TTL for AI voice replies; long enough that a candidate
     *  who pauses to think can still replay the question without it 403'ing. */
    private static final long TTS_URL_TTL_SECONDS = 30 * 60;

    /** Question angles rotated randomly so every session opens with a different focus. */
    private static final String[] OPENING_ANGLES = {
        "你最近解决过的一个具体技术难题",
        "你做过的一次系统设计或方案取舍，以及背后的原因",
        "你排查过的一次复杂问题，以及定位过程",
        "你如何保证代码清晰、可维护",
        "你最有代表性的项目，以及你在其中的贡献",
        "你和同伴在技术方案上有分歧时如何处理",
        "你发现并优化过的一个性能瓶颈",
        "你使用过的测试策略，以及为什么这样选择",
        "你犯过的一次错误，以及从中学到了什么",
        "你如何持续学习目标岗位需要的新技术",
    };
    private static final Random RNG = new Random();

    @Operation(summary = "Start a fresh interview session. Any existing ONGOING sessions are cancelled " +
            "so the candidate always gets a new opening question.")
    @PostMapping("/start")
    public Result<Interview> startInterview(@RequestBody StartInterviewRequest request) {
        Long uid = SecurityUtil.requireCurrentUserId();

        // Cancel every lingering ONGOING session before opening a new one.
        // Leaving stale sessions open caused every /voice-greeting call to replay
        // the same saved greeting — the candidate thought the questions were "fixed".
        interviewService.getUserInterviews(uid)
                .stream()
                .filter(i -> "ONGOING".equals(i.getStatus()))
                .forEach(i -> {
                    try {
                        interviewService.cancelInterview(i.getInterviewId());
                    } catch (Exception ignored) {
                        // best-effort: don't block the new session if cleanup fails
                    }
                });

        Interview interview = interviewService.startInterview(
                uid,
                request.getResumeId(),
                request.getPositionName(),
                request.getDifficulty(),
                request.getMode()
        );
        return Result.success(interview);
    }

    @Operation(summary = "Generate the AI interviewer's opening question. Idempotent: " +
            "returns the existing first message if the conversation is non-empty.")
    @PostMapping("/{interviewId}/greeting")
    public Result<InterviewMessage> generateGreeting(
            @PathVariable Long interviewId,
            @RequestParam(required = false, defaultValue = "zh") String language) {
        Long uid = SecurityUtil.requireCurrentUserId();
        Interview interview = requireOngoing(interviewId, uid);

        List<InterviewMessage> existing = interviewService.getInterviewMessages(interviewId);
        if (!existing.isEmpty()) {
            return Result.success(existing.get(0));
        }

        // 50% of sessions try to draw from the crowd-sourced bank when a
        // matching (position, difficulty) question exists. This keeps repeat
        // candidates from cycling through only the 10 baked-in angles.
        Optional<InterviewQuestion> drawn = questionBankService.drawForInterview(
                interview.getPositionName(), interview.getDifficulty());
        String angle = drawn.map(InterviewQuestion::getContent)
                .orElseGet(() -> OPENING_ANGLES[RNG.nextInt(OPENING_ANGLES.length)]);
        String prompt = String.format(
                "你是“%s”岗位的资深面试官，当前难度：%s。请先用一句话简短问候候选人，然后提出第一个面试问题。 " +
                "问题必须围绕：%s。不要解释评分标准，不要列追问，不要使用 Markdown。%s",
                interview.getPositionName(),
                interview.getDifficulty() == null ? "Normal" : interview.getDifficulty(),
                angle,
                langInstruction(language)
        );
        String greeting = aiService.chat(prompt);
        if (greeting == null || greeting.isBlank()) {
            throw new BizException("AI failed to generate the opening question");
        }
        interviewService.sendMessage(interviewId, "AI", greeting.trim());
        return Result.success(interviewService.getInterviewMessages(interviewId).get(0));
    }

    @Operation(summary = "Send a message in interview (with AI response)")
    @PostMapping("/{interviewId}/message")
    public Result<MessageResponse> sendMessage(
            @PathVariable Long interviewId,
            @RequestBody SendMessageRequest request
    ) {
        Long uid = SecurityUtil.requireCurrentUserId();
        Interview interview = requireOngoing(interviewId, uid);

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BizException("Message content is required");
        }

        // 1. Save user message
        interviewService.sendMessage(interviewId, "USER", request.getContent());

        // 2. Build chat completion payload (system + full transcript so far)
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", String.format(
                "你是“%s”岗位的专业面试官，当前难度：%s。请先简短评价候选人上一轮回答，再提出下一个聚焦问题。 " +
                "回复控制在 2-4 句，不要使用 Markdown。%s",
                interview.getPositionName(),
                interview.getDifficulty() == null ? "Normal" : interview.getDifficulty(),
                langInstruction(request.getLanguage())
        ));
        messages.add(systemMsg);

        for (InterviewMessage msg : interviewService.getInterviewMessages(interviewId)) {
            Map<String, String> historyMsg = new HashMap<>();
            historyMsg.put("role", "USER".equalsIgnoreCase(msg.getRole()) ? "user" : "assistant");
            historyMsg.put("content", msg.getContent());
            messages.add(historyMsg);
        }

        String aiResponse = aiService.chat(messages);
        interviewService.sendMessage(interviewId, "AI", aiResponse);

        MessageResponse response = new MessageResponse();
        response.setUserMessage(request.getContent());
        response.setAiMessage(aiResponse);
        return Result.success(response);
    }

    @Operation(summary = "Voice greeting — synthesize the AI interviewer's opening question " +
            "as audio. Idempotent: reuses the existing greeting text if one was already saved.")
    @PostMapping("/{interviewId}/voice-greeting")
    public Result<VoiceTurnResponse> voiceGreeting(
            @PathVariable Long interviewId,
            @RequestParam(required = false, defaultValue = "zh") String language) {
        Long uid = SecurityUtil.requireCurrentUserId();
        Interview interview = requireOngoing(interviewId, uid);

        // Reuse text if it's already there — TTS itself will re-synthesize so
        // the candidate gets a freshly-signed URL each time, but we never want
        // the AI to re-greet mid-session.
        List<InterviewMessage> existing = interviewService.getInterviewMessages(interviewId);
        String greeting;
        if (!existing.isEmpty() && "AI".equalsIgnoreCase(existing.get(0).getRole())) {
            greeting = existing.get(0).getContent();
        } else {
            Optional<InterviewQuestion> drawn = questionBankService.drawForInterview(
                    interview.getPositionName(), interview.getDifficulty());
            String angle = drawn.map(InterviewQuestion::getContent)
                    .orElseGet(() -> OPENING_ANGLES[RNG.nextInt(OPENING_ANGLES.length)]);
            String prompt = String.format(
                    "你是“%s”岗位的资深面试官，当前难度：%s，正在进行语音面试。请用一句话简短问候候选人，" +
                    "然后提出一个围绕“%s”的面试问题。%s 不要寒暄过多，不要使用 Markdown，适合直接朗读，总字数不超过 90 个汉字。",
                    interview.getPositionName(),
                    interview.getDifficulty() == null ? "Normal" : interview.getDifficulty(),
                    angle,
                    langInstruction(language)
            );
            greeting = aiService.chat(prompt);
            if (greeting == null || greeting.isBlank()) {
                throw new BizException("AI failed to generate the greeting");
            }
            greeting = greeting.trim();
            interviewService.sendMessage(interviewId, "AI", greeting);
        }

        VoiceService.TtsResult tts = voiceService.synthesize(uid, greeting);
        String audioUrl = fileService.presignedUrl(tts.objectKey(), TTS_URL_TTL_SECONDS);

        VoiceTurnResponse r = new VoiceTurnResponse();
        r.setAiText(greeting);
        r.setAudioKey(tts.objectKey());
        r.setAudioUrl(audioUrl);
        r.setDurationMs(tts.durationMs());
        return Result.success(r);
    }

    /**
     * One full voice turn: the candidate sends a recorded clip, we transcribe
     * it, run it through Qwen with the running interview transcript, then
     * synthesize the AI's reply and hand back a playable URL.
     *
     * <p>Why a single endpoint instead of three (asr/chat/tts)? Each turn is
     * tightly coupled — the user expects sub-5s end-to-end before the digital
     * human starts speaking. Round-tripping over the WeChat HTTPS layer twice
     * more would routinely tip past that budget on a 4G connection.</p>
     */
    @Operation(summary = "Voice turn — upload a recorded answer, get back the transcript, " +
            "the AI interviewer's reply text, and a presigned URL for the AI's spoken reply.")
    @PostMapping(value = "/{interviewId}/voice-turn", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<VoiceTurnResponse> voiceTurn(
            @PathVariable Long interviewId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "format", defaultValue = "mp3") String format,
            @RequestParam(value = "language", defaultValue = "zh") String language
    ) {
        Long uid = SecurityUtil.requireCurrentUserId();
        Interview interview = requireOngoing(interviewId, uid);

        if (audio == null || audio.isEmpty()) {
            throw new BizException("Audio file is required");
        }
        // Mirror the spring multipart cap; let the user know up front rather
        // than throw the (less friendly) MaxUploadSizeExceededException.
        if (audio.getSize() > 10L * 1024 * 1024) {
            throw new BizException("Audio too large (max 10 MB; please record under 60 seconds)");
        }

        long t0 = System.currentTimeMillis();
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            log.warn("Failed to read an uploaded interview audio clip", e);
            throw new BizException("Failed to read uploaded audio");
        }

        String userText = voiceService.transcribe(bytes, format);
        long t1 = System.currentTimeMillis();
        if (userText == null || userText.isBlank()) {
            throw new BizException("没有识别到有效语音，请说清楚后重试。");
        }

        // Persist the user turn before generating the AI reply so the chat
        // history fed to Qwen always reflects what we just heard.
        interviewService.sendMessage(interviewId, "USER", userText);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", String.format(
                "你是“%s”岗位的专业面试官，当前难度：%s，正在进行语音面试。请先用一句话评价候选人的上一轮回答，" +
                "再问一个聚焦的下一题。总共 1-2 句，适合直接朗读。%s 不要使用 Markdown，不要列表，不要表情符号。",
                interview.getPositionName(),
                interview.getDifficulty() == null ? "Normal" : interview.getDifficulty(),
                langInstruction(language)
        ));
        messages.add(systemMsg);

        for (InterviewMessage m : interviewService.getInterviewMessages(interviewId)) {
            Map<String, String> n = new HashMap<>();
            n.put("role", "USER".equalsIgnoreCase(m.getRole()) ? "user" : "assistant");
            n.put("content", m.getContent());
            messages.add(n);
        }
        String aiText = aiService.chat(messages);
        if (aiText == null || aiText.isBlank()) {
            throw new BizException("AI returned empty response");
        }
        aiText = stripMarkdown(aiText.trim());
        interviewService.sendMessage(interviewId, "AI", aiText);
        long t2 = System.currentTimeMillis();

        VoiceService.TtsResult tts = voiceService.synthesize(uid, aiText);
        String audioUrl = fileService.presignedUrl(tts.objectKey(), TTS_URL_TTL_SECONDS);
        long t3 = System.currentTimeMillis();

        log.info("[voice-turn] interviewId={} asr={}ms ai={}ms tts={}ms total={}ms",
                interviewId, t1 - t0, t2 - t1, t3 - t2, t3 - t0);

        VoiceTurnResponse r = new VoiceTurnResponse();
        r.setUserText(userText);
        r.setAiText(aiText);
        r.setAudioKey(tts.objectKey());
        r.setAudioUrl(audioUrl);
        r.setDurationMs(tts.durationMs());
        return Result.success(r);
    }

    @Operation(summary = "Get interview message history")
    @GetMapping("/{interviewId}/messages")
    public Result<List<InterviewMessage>> getMessages(@PathVariable Long interviewId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        interviewService.assertOwnership(interviewId, uid);
        return Result.success(interviewService.getInterviewMessages(interviewId));
    }

    @Operation(summary = "End interview. Final score is computed lazily by the report endpoint.")
    @PostMapping("/{interviewId}/end")
    public Result<Interview> endInterview(@PathVariable Long interviewId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        interviewService.assertOwnership(interviewId, uid);
        // No score is taken from the client. With no candidate answer the
        // service returns CANCELLED; otherwise it transitions to COMPLETED.
        return Result.success(interviewService.endInterview(interviewId, null));
    }

    @Operation(summary = "Get the current user's interviews (preferred)")
    @GetMapping("/user/me")
    public Result<List<Interview>> getMyInterviews() {
        Long uid = SecurityUtil.requireCurrentUserId();
        return Result.success(interviewService.getUserInterviews(uid));
    }

    @Operation(summary = "Get user's all interviews (legacy: path userId must equal JWT subject)")
    @GetMapping("/user/{userId}")
    public Result<List<Interview>> getUserInterviews(@PathVariable Long userId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        if (!uid.equals(userId)) {
            throw new BizException("Cannot list another user's interviews");
        }
        return Result.success(interviewService.getUserInterviews(userId));
    }

    @Operation(summary = "Get interview by ID")
    @GetMapping("/{interviewId}")
    public Result<Interview> getInterview(@PathVariable Long interviewId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        return Result.success(interviewService.assertOwnership(interviewId, uid));
    }

    @Operation(summary = "Delete an interview session and its messages (owner-only)")
    @DeleteMapping("/{interviewId}")
    public Result<String> deleteInterview(@PathVariable Long interviewId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        interviewService.deleteInterview(uid, interviewId);
        return Result.success("面试记录已删除");
    }

    /**
     * Returns the language instruction appended to every AI interview prompt.
     * "zh" means Chinese. Chinese is the safe default for this product stage.
     */
    private static String langInstruction(String language) {
        return "en".equalsIgnoreCase(language)
                ? "如果用户明确选择英文模式，可以使用英文。"
                : "请全程使用简体中文进行面试，包括提问、追问和反馈，不要夹杂英文标签。";
    }

    private Interview requireOngoing(Long interviewId, Long userId) {
        Interview interview = interviewService.assertOwnership(interviewId, userId);
        if (!"ONGOING".equals(interview.getStatus())) {
            throw new BizException("Interview is no longer active");
        }
        return interview;
    }

    /** Remove common markdown artifacts that LLMs sometimes emit despite "plain text" instructions. */
    private static String stripMarkdown(String text) {
        if (text == null) return "";
        return text
                .replaceAll("```[\\w]*\\n?", "")   // fenced code blocks
                .replaceAll("`([^`]+)`", "$1")       // inline code
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1") // bold
                .replaceAll("\\*([^*]+)\\*", "$1")   // italic
                .replaceAll("^#+\\s+", "")            // headings
                .replaceAll("^[-*]\\s+", "")          // list bullets
                .trim();
    }

    // ================= DTO Classes =================

    @Data
    public static class StartInterviewRequest {
        // userId is intentionally absent; resolved from JWT
        private Long resumeId;
        private String positionName;
        private String difficulty; // Easy, Normal, Hard
        /** TEXT (default) or VOICE — drives History resume routing. */
        private String mode;
    }

    @Data
    public static class SendMessageRequest {
        private String content;
        /** "zh" for Chinese interview. Chinese is the product default. */
        private String language;
    }

    @Data
    public static class MessageResponse {
        private String userMessage;
        private String aiMessage;
    }

    /**
     * Used by both {@code voice-greeting} and {@code voice-turn}.
     * {@code userText} is null on the greeting path (no candidate input yet).
     */
    @Data
    public static class VoiceTurnResponse {
        private String userText;
        private String aiText;
        /** OSS object key for the TTS mp3 — kept so the client can replay
         *  via a fresh presign if the original {@code audioUrl} expires. */
        private String audioKey;
        /** Short-lived presigned URL safe to drop straight into {@code <audio src=...>}. */
        private String audioUrl;
        /** Coarse duration estimate; UI should prefer the player's
         *  loadedmetadata reading once available. */
        private Long durationMs;
    }
}
