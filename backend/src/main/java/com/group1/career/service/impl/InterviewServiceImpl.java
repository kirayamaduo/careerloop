package com.group1.career.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.dto.UserProfileSnapshot;
import com.group1.career.model.entity.Interview;
import com.group1.career.model.entity.InterviewMessage;
import com.group1.career.repository.InterviewMessageRepository;
import com.group1.career.repository.InterviewRepository;
import com.group1.career.service.CheckInService;
import com.group1.career.service.BodyLanguageService;
import com.group1.career.model.NotificationTypes;
import com.group1.career.service.InterviewService;
import com.group1.career.service.NotificationService;
import com.group1.career.service.UserProfileSnapshotService;
import com.group1.career.service.WechatSubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewMessageRepository messageRepository;
    private final NotificationService notificationService;
    private final UserProfileSnapshotService snapshotService;
    private final CheckInService checkInService;
    private final BodyLanguageService bodyLanguageService;
    private final WechatSubscribeService wechatSubscribeService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Interview startInterview(Long userId, Long resumeId, String positionName, String difficulty, String mode) {
        String normalizedMode = (mode == null || mode.isBlank()) ? "TEXT" : mode.trim().toUpperCase();
        if (!"TEXT".equals(normalizedMode) && !"VOICE".equals(normalizedMode)) {
            normalizedMode = "TEXT";
        }
        Interview interview = Interview.builder()
                .userId(userId)
                .resumeId(resumeId)
                .positionName(positionName)
                .difficulty(difficulty)
                .mode(normalizedMode)
                .status("ONGOING")
                .build();

        Interview saved = interviewRepository.save(interview);
        log.info("Started interview {} for user {} (mode={})", saved.getInterviewId(), userId, normalizedMode);
        return saved;
    }

    @Override
    @Transactional
    public void sendMessage(Long interviewId, String role, String content) {
        InterviewMessage message = InterviewMessage.builder()
                .interviewId(interviewId)
                .role(role)
                .content(content)
                .build();
        messageRepository.save(message);
    }

    @Override
    public List<InterviewMessage> getInterviewMessages(Long interviewId) {
        return messageRepository.findByInterviewIdOrderByCreatedAtAsc(interviewId);
    }

    @Override
    @Transactional
    public Interview endInterview(Long interviewId, Integer finalScore) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR));

        if (!"ONGOING".equals(interview.getStatus())) {
            return interview;
        }

        boolean hasCandidateAnswer =
                messageRepository.existsByInterviewIdAndRoleIgnoreCase(interviewId, "USER");
        if (!hasCandidateAnswer) {
            return cancelInterview(interviewId);
        }

        interview.setStatus("COMPLETED");
        interview.setFinalScore(finalScore);
        finishTiming(interview);

        Interview saved = interviewRepository.save(interview);

        // The detailed report (with strong/weak dimensions) hasn't landed
        // yet at this point -- saveReport will refresh the snapshot once
        // the AI report comes back. Here we just record the position+score.
        mergeIntoSnapshot(saved, null);

        // Daily check-in. Best-effort.
        try {
            checkInService.recordAction(saved.getUserId(), "INTERVIEW");
        } catch (Exception e) {
            log.warn("[interview] check-in record failed for user {}: {}", saved.getUserId(), e.toString());
        }

        return saved;
    }

    @Override
    @Transactional
    public Interview cancelInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR));
        if (!"ONGOING".equals(interview.getStatus())) {
            return interview;
        }
        interview.setStatus("CANCELLED");
        interview.setFinalScore(null);
        finishTiming(interview);
        Interview saved = interviewRepository.save(interview);
        bodyLanguageService.clear(interviewId);
        log.info("Cancelled interview {} without completion side effects", interviewId);
        return saved;
    }

    @Override
    public List<Interview> getUserInterviews(Long userId) {
        return interviewRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    @Override
    public Interview getInterviewById(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR));
    }

    @Override
    public Interview assertOwnership(Long interviewId, Long userId) {
        Interview interview = getInterviewById(interviewId);
        if (!interview.getUserId().equals(userId)) {
            log.warn("Ownership violation: user {} tried to access interview {} owned by {}",
                    userId, interviewId, interview.getUserId());
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return interview;
    }

    @Override
    public Interview lockForReport(Long interviewId, Long userId) {
        Interview interview = interviewRepository.findByIdForUpdate(interviewId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR));
        if (!interview.getUserId().equals(userId)) {
            log.warn("Ownership violation: user {} tried to generate report for interview {} owned by {}",
                    userId, interviewId, interview.getUserId());
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return interview;
    }

    @Override
    @Transactional
    public Interview saveReport(Long interviewId, String reportJson, Integer overallScore) {
        Interview interview = getInterviewById(interviewId);
        if (!"COMPLETED".equals(interview.getStatus())) {
            throw new BizException("Only completed interviews can generate reports");
        }
        boolean firstReport = interview.getReportJson() == null || interview.getReportJson().isBlank();
        interview.setReportJson(reportJson);
        if (overallScore != null) {
            interview.setFinalScore(overallScore);
        }
        Interview saved = interviewRepository.save(interview);
        mergeIntoSnapshot(saved, reportJson);
        if (firstReport) {
            notifyReportReadyAfterCommit(saved);
        }
        return saved;
    }

    @Override
    @Transactional
    public void deleteInterview(Long userId, Long interviewId) {
        Interview interview = assertOwnership(interviewId, userId);
        messageRepository.deleteByInterviewId(interviewId);
        interviewRepository.delete(interview);
        log.info("Deleted interview {} for user {}", interviewId, userId);
    }

    /**
     * Merge the latest interview into the user's cross-tool portrait so the
     * AI assistant can give specific, grounded coaching ("you scored 78 on
     * your last frontend interview, weak in communication") instead of
     * generic copy. Best-effort -- never fail the interview write because
     * the snapshot blew up.
     *
     * <p>{@code reportJson} may be null when called from {@link #endInterview}
     * (the AI report hasn't been generated yet); in that case we just record
     * the position and score, and the next {@link #saveReport} call will
     * refresh the strong/weak dimensions.
     */
    private void mergeIntoSnapshot(Interview interview, String reportJson) {
        if (interview == null || interview.getUserId() == null) return;
        try {
            List<String> strong = new ArrayList<>();
            List<String> weak = new ArrayList<>();
            if (reportJson != null && !reportJson.isBlank()) {
                extractDimensions(reportJson, strong, weak);
            }
            snapshotService.mergeInterview(interview.getUserId(), UserProfileSnapshot.InterviewBlock.builder()
                    .lastInterviewId(interview.getInterviewId())
                    .positionName(interview.getPositionName())
                    .difficulty(interview.getDifficulty())
                    .lastScore(interview.getFinalScore())
                    .strongDimensions(strong.isEmpty() ? null : strong)
                    .weakDimensions(weak.isEmpty() ? null : weak)
                    .completedAt(interview.getEndedAt() != null ? interview.getEndedAt() : LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[interview] snapshot merge failed for interview {}: {}",
                    interview.getInterviewId(), e.toString());
        }
    }

    /**
     * Walk the report JSON and tag each dimension as strong (>=80) or weak
     * (<60). Two shapes are supported so older reports keep merging:
     * <ul>
     *   <li>Current — {@code {"radarChart": {"expression": 78, "logic": 82, ...}}}
     *       produced by {@code InterviewReportController}.</li>
     *   <li>Legacy — {@code {"dimensions": [{"name": "logic", "score": 82}, ...]}}.</li>
     * </ul>
     * Any parse failure leaves both lists empty rather than failing the merge.
     */
    private void extractDimensions(String reportJson, List<String> strong, List<String> weak) {
        try {
            JsonNode root = objectMapper.readTree(reportJson);

            // Preferred shape: radarChart object whose every numeric field is a dimension.
            JsonNode radar = root.get("radarChart");
            if (radar != null && radar.isObject()) {
                radar.fields().forEachRemaining(e -> {
                    JsonNode v = e.getValue();
                    if (v != null && v.isNumber()) {
                        bucket(e.getKey(), v.asInt(), strong, weak);
                    }
                });
                if (!strong.isEmpty() || !weak.isEmpty()) return;
            }

            // Legacy shape kept for any reports written before the radarChart cut-over.
            JsonNode dims = root.get("dimensions");
            if (dims != null && dims.isArray()) {
                for (JsonNode d : dims) {
                    JsonNode nameNode = d.get("name");
                    JsonNode scoreNode = d.get("score");
                    if (nameNode == null || !nameNode.isTextual()
                            || scoreNode == null || !scoreNode.isNumber()) continue;
                    bucket(nameNode.asText(), scoreNode.asInt(), strong, weak);
                }
            }
        } catch (Exception e) {
            log.debug("[interview] could not parse report for dimensions: {}", e.toString());
        }
    }

    private void bucket(String name, int score, List<String> strong, List<String> weak) {
        if (name == null || name.isBlank()) return;
        if (score >= 80) strong.add(name);
        else if (score < 60) weak.add(name);
    }

    private void finishTiming(Interview interview) {
        interview.setEndedAt(LocalDateTime.now());
        if (interview.getStartedAt() == null) return;
        Duration duration = Duration.between(interview.getStartedAt(), interview.getEndedAt());
        interview.setDurationSeconds((int) Math.max(0, duration.getSeconds()));
    }

    private void notifyReportReadyAfterCommit(Interview interview) {
        Runnable notify = () -> {
            try {
                notificationService.push(
                        interview.getUserId(),
                        NotificationTypes.INTERVIEW_REPORT,
                        "面试报告已生成",
                        "你的“" + interview.getPositionName() + "”模拟面试报告已就绪，点击查看 AI 复盘。",
                        "/pages/interview/report?interviewId=" + interview.getInterviewId()
                );
            } catch (Exception e) {
                log.warn("[interview] report notification failed for interview {}: exception={}",
                        interview.getInterviewId(), e.getClass().getSimpleName());
            }
            try {
                wechatSubscribeService.sendInterviewReport(
                        interview.getUserId(),
                        interview.getFinalScore() == null ? 0 : interview.getFinalScore(),
                        interview.getPositionName()
                );
            } catch (Exception e) {
                log.warn("[interview] wx report notification failed for interview {}: {}",
                        interview.getInterviewId(), e.getClass().getSimpleName());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notify.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notify.run();
            }
        });
    }
}
