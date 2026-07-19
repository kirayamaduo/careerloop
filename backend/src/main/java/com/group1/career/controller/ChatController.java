package com.group1.career.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.common.Result;
import com.group1.career.service.AgentProfileService;
import com.group1.career.service.AiService;
import com.group1.career.service.ConversationSummaryService;
import com.group1.career.service.UserFactService;
import com.group1.career.service.UserProfileSnapshotService;
import com.group1.career.service.UserProfileTagService;
import com.group1.career.service.ai.FunctionCallingService;
import com.group1.career.config.AiPersonas;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Chat Assistant API", description = "Global AI career assistant with SSE streaming")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AiService aiService;
    private final FunctionCallingService functionCallingService;
    private final UserProfileSnapshotService snapshotService;
    private final AgentProfileService agentProfileService;
    private final ConversationSummaryService summaryService;
    private final UserFactService userFactService;
    private final UserProfileTagService profileTagService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** F15: persona-aware base prompt is delegated to {@link AiPersonas}. */

    @Operation(summary = "Send chat message (synchronous response)")
    @PostMapping("/send")
    public Result<ChatResponseDto> sendMessage(@RequestBody ChatRequestDto request) {
        List<Map<String, String>> messages = buildMessages(request.getHistory(), request.getMessage(), request.getPersona());
        Long uid = SecurityUtil.currentUserId();
        // F13: use function calling for authenticated users; fall back to plain chat for guests
        String reply = (uid != null)
                ? functionCallingService.chat(messages, uid)
                : aiService.chat(messages);

        ChatResponseDto response = new ChatResponseDto();
        response.setReply(reply);
        return Result.success(response);
    }

    @Operation(summary = "Send chat message (SSE streaming response)")
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamMessage(
            @RequestParam String message,
            @RequestParam(required = false) String historyJson,
            @RequestParam(required = false) String persona,
            @RequestParam(required = false) Long sessionId) {

        List<Map<String, String>> history = parseHistory(historyJson);
        List<Map<String, String>> messages = buildMessages(history, message, persona);

        return aiService.streamChat(messages);
    }

    /**
     * Decode the conversation history that the frontend serialised on the
     * query string. We tolerate both raw JSON and URL-encoded payloads, and
     * silently fall back to an empty list so a corrupt history never breaks
     * the live message — the user just loses long-term context for that turn.
     */
    private List<Map<String, String>> parseHistory(String historyJson) {
        if (historyJson == null || historyJson.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(historyJson, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse chat history JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, String>> buildMessages(List<Map<String, String>> history, String userMessage, String persona) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildSystemPrompt(persona));
        messages.add(systemMsg);

        if (history != null) {
            messages.addAll(history);
        }

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return messages;
    }

    /**
     * F11: Compose system prompt = base persona + cross-tool snapshot + rolling memory summary.
     */
    private String buildSystemPrompt(String persona) {
        Long uid = SecurityUtil.currentUserId();
        if (uid == null) return AiPersonas.systemPromptFor(persona);

        StringBuilder sb = new StringBuilder(AiPersonas.systemPromptFor(persona));

        String snapshot = agentProfileService.renderForPrompt(uid);
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = snapshotService.renderForPrompt(uid);
        }
        if (snapshot != null && !snapshot.isBlank()) {
            sb.append("\n\n").append(snapshot);
        }

        String p = (persona != null && !persona.isBlank()) ? persona : "MENTOR";
        String memory = summaryService.getLatestSummary(uid, p);
        if (!memory.isBlank()) {
            sb.append("\n\n[LONG-TERM MEMORY — past conversation summary]\n").append(memory);
        }

        String facts = userFactService.renderForPrompt(uid);
        if (!facts.isBlank()) {
            sb.append("\n\n").append(facts);
        }

        String tags = profileTagService.renderForPrompt(uid);
        if (!tags.isBlank()) {
            sb.append("\n\n").append(tags);
        }

        return sb.toString();
    }

    // ================= DTO Classes =================

    @Data
    public static class ChatRequestDto {
        private String message;
        private List<Map<String, String>> history;
        /** F15/F11: MENTOR | CHALLENGER. Defaults to MENTOR if absent. */
        private String persona;
        /**
         * Kept for wire compatibility. Durable roll-up now runs only after
         * /history/.../append commits the complete user/assistant pair.
         */
        private Long sessionId;
    }

    @Data
    public static class ChatResponseDto {
        private String reply;
    }
}
