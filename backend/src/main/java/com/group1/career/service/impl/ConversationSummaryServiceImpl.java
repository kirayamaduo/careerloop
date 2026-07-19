package com.group1.career.service.impl;

import com.group1.career.model.entity.AssistantMessage;
import com.group1.career.model.entity.ConversationSummary;
import com.group1.career.repository.AssistantMessageRepository;
import com.group1.career.repository.ConversationSummaryRepository;
import com.group1.career.service.AiService;
import com.group1.career.service.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F11: qwen-turbo rolling summary implementation.
 *
 * <p>Threshold: whenever a user/persona pair accumulates at least
 * {@value #ROLL_THRESHOLD} messages after its durable cursor, a summary is
 * produced from the next window and the cursor is advanced. Messages remain
 * available for audit; they are never deleted by the roll-up.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    private final AssistantMessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final AiService aiService;

    /** Messages in a session before a roll-up is triggered. */
    private static final int ROLL_THRESHOLD = 20;

    /** How many messages to include in the summarization prompt. */
    private static final int ROLL_WINDOW = 20;

    /**
     * Serializes roll-ups for the same user/persona inside this JVM. The
     * entity's optimistic version remains the cross-instance safety net.
     */
    private final Map<String, Object> rollupLocks = new ConcurrentHashMap<>();

    @Override
    public String getLatestSummary(Long userId, String persona) {
        return summaryRepository.findByUserIdAndPersona(userId, normalizePersona(persona))
                .map(ConversationSummary::getSummaryText)
                .orElse("");
    }

    @Async
    @Override
    public void triggerRollupIfNeeded(Long userId, String persona, Long sessionId) {
        if (userId == null || sessionId == null) return;
        String normalizedPersona = normalizePersona(persona);
        Object lock = rollupLocks.computeIfAbsent(userId + ":" + normalizedPersona, ignored -> new Object());

        synchronized (lock) {
            rollUpAvailableWindows(userId, normalizedPersona, sessionId);
        }
    }

    private void rollUpAvailableWindows(Long userId, String persona, Long sessionId) {
        try {
            while (true) {
                Optional<ConversationSummary> existing =
                        summaryRepository.findByUserIdAndPersona(userId, persona);
                long cursor = existing.map(ConversationSummary::getLastMessageId).orElse(0L);
                List<AssistantMessage> pending =
                        messageRepository.findUnsummarized(userId, persona, cursor);
                if (pending.size() < ROLL_THRESHOLD) return;

                List<AssistantMessage> window =
                        pending.subList(0, Math.min(ROLL_WINDOW, pending.size()));
                log.info("[F11] Rolling summary for user={} persona={} session={} cursor={} window={}",
                        userId, persona, sessionId, cursor, window.size());

                StringBuilder transcript = new StringBuilder();
                for (AssistantMessage message : window) {
                    transcript.append(message.getRole().name())
                            .append(": ")
                            .append(message.getContent())
                            .append("\n");
                }

                String previousSummary = existing.map(ConversationSummary::getSummaryText).orElse("");
                String prompt = buildSummaryPrompt(previousSummary, transcript.toString());
                List<Map<String, String>> promptMessages = new ArrayList<>();
                promptMessages.add(Map.of("role", "user", "content", prompt));

                String newSummary = aiService.chat(promptMessages, "qwen-turbo");
                if (isFailedResponse(newSummary)) {
                    log.warn("[F11] Summary generation failed for user={} persona={}, cursor remains {}",
                            userId, persona, cursor);
                    return;
                }

                ConversationSummary row = existing.orElseGet(() -> ConversationSummary.builder()
                        .userId(userId)
                        .persona(persona)
                        .build());
                long newCursor = window.get(window.size() - 1).getMsgId();
                row.setSummaryText(newSummary.trim());
                row.setTurnCount((row.getTurnCount() == null ? 0 : row.getTurnCount()) + window.size());
                row.setLastMessageId(newCursor);
                row.setModelUsed("qwen-turbo");
                summaryRepository.saveAndFlush(row);

                log.info("[F11] Summary saved for user={} persona={} turns={} cursor={}",
                        userId, persona, row.getTurnCount(), newCursor);
            }
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            // Another application instance committed the same window first.
            // Never overwrite it with an older async result; the next append
            // will trigger a fresh check from the durable cursor.
            log.info("[F11] Concurrent rollup won for user={} persona={}; stale result discarded",
                    userId, persona);
        } catch (Exception e) {
            log.error("[F11] Rollup failed for user={} persona={}: {}", userId, persona, e.getMessage(), e);
        }
    }

    private boolean isFailedResponse(String response) {
        if (response == null || response.isBlank()) return true;
        String normalized = response.trim();
        return normalized.startsWith("Error") || normalized.startsWith("AI service busy");
    }

    private String normalizePersona(String persona) {
        return persona == null || persona.isBlank() ? "MENTOR" : persona.trim().toUpperCase();
    }

    private String buildSummaryPrompt(String prevSummary, String transcript) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a concise memory assistant. Summarize the following conversation into 3-5 sentences. ");
        sb.append("Focus on: the user's career goals, skills discussed, advice given, and any commitments made. ");
        sb.append("Reply ONLY with the summary paragraph, no preamble.\n\n");
        if (!prevSummary.isBlank()) {
            sb.append("PREVIOUS SUMMARY:\n").append(prevSummary).append("\n\n");
            sb.append("NEW CONVERSATION (extends the above):\n");
        } else {
            sb.append("CONVERSATION:\n");
        }
        sb.append(transcript);
        return sb.toString();
    }
}
