package com.group1.career.service;

import java.util.Map;

/**
 * F10: WeChat subscribe message service.
 *
 * <p>Handles access-token retrieval (Redis-cached, 2 h TTL) and sending
 * template-based subscribe messages to users who have granted permission via
 * {@code wx.requestSubscribeMessage}.</p>
 */
public interface WechatSubscribeService {

    /**
     * Return the mini-program template IDs that are both configured on this
     * server and backed by a real send flow.
     *
     * <p>The keys are the stable frontend contract:
     * {@code weekly}, {@code assessment}, and {@code interview}. Unconfigured
     * templates are omitted.</p>
     */
    Map<String, String> getConfiguredTemplates();

    /**
     * Record that a user has granted subscribe permission for one or more templates.
     * Increments {@code remaining} in {@code wx_subscribe_quota} for each accepted template.
     *
     * @param userId    the authenticated user
     * @param accepted  map of templateId → "accept" | "reject" | "ban"
     */
    void recordGrant(Long userId, Map<String, String> accepted);

    /**
     * Send a subscribe message to the user if they have remaining quota.
     * Decrements quota atomically. Does nothing if no quota or template ID is blank.
     *
     * @param userId     recipient user ID
     * @param templateId WeChat template ID
     * @param page       mini-program page path to redirect on click (e.g. "/pages/interview/result")
     * @param data       template data map (key → value string pairs sent to WX API)
     * @return true if the message was dispatched, false if skipped (no quota / no openid / blank template)
     */
    boolean send(Long userId, String templateId, String page, Map<String, String> data);

    /**
     * Convenience overload: send the weekly-report template.
     */
    boolean sendWeeklyReport(Long userId, String summaryText);

    /**
     * Convenience overload: send the interview-report template.
     */
    boolean sendInterviewReport(Long userId, int score, String position);

    /**
     * Convenience overload: send the assessment-result template.
     */
    boolean sendAssessmentResult(Long userId, String scaleTitle, String result);

    /**
     * Convenience overload: send AI proactive message.
     */
    boolean sendAiProactive(Long userId, String messageText);
}
