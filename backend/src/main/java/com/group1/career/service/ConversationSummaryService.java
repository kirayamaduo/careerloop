package com.group1.career.service;

/**
 * F11: Rolling AI conversation memory via qwen-turbo summarization.
 *
 * <p>After every N turns this service condenses the session history into
 * a concise paragraph and persists it in {@code conversation_summaries}.
 * On subsequent chat calls the summary is injected into the system prompt
 * so the model retains long-term context without re-sending full history.</p>
 */
public interface ConversationSummaryService {

    /**
     * Return the most recent summary paragraph for this (user, persona) pair,
     * or an empty string if no summary exists yet.
     */
    String getLatestSummary(Long userId, String persona);

    /**
     * Asynchronously check if the session has crossed the roll-up threshold
     * and, if so, produce a new rolling summary via qwen-turbo.
     *
     * <p>This is a fire-and-forget call — callers don't block on it.</p>
     *
     * @param userId    owner of the session
     * @param persona   MENTOR | CHALLENGER
     * @param sessionId session whose committed append triggered this check;
     *                  the durable cursor spans every session for the persona
     */
    void triggerRollupIfNeeded(Long userId, String persona, Long sessionId);
}
