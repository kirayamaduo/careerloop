package com.group1.career.service;

import com.group1.career.model.entity.Interview;
import com.group1.career.model.entity.InterviewMessage;

import java.util.List;

public interface InterviewService {
    /**
     * Start a new interview.
     *
     * @param mode "TEXT" or "VOICE" — drives where the History page resumes
     *             ONGOING sessions; nullable, defaults to TEXT.
     */
    Interview startInterview(Long userId, Long resumeId, String positionName, String difficulty, String mode);

    /**
     * Backwards-compatible overload used by older callers / tests. Defaults
     * the modality to TEXT.
     */
    default Interview startInterview(Long userId, Long resumeId, String positionName, String difficulty) {
        return startInterview(userId, resumeId, positionName, difficulty, "TEXT");
    }

    /**
     * Send a message in interview
     */
    void sendMessage(Long interviewId, String role, String content);

    /**
     * Get interview history messages
     */
    List<InterviewMessage> getInterviewMessages(Long interviewId);

    /**
     * End interview and calculate final score
     */
    Interview endInterview(Long interviewId, Integer finalScore);

    /**
     * Abandon an ongoing session without counting it as a completed interview,
     * check-in, profile signal, or report-ready event.
     */
    Interview cancelInterview(Long interviewId);

    /**
     * Get user's all interviews
     */
    List<Interview> getUserInterviews(Long userId);

    /**
     * Get interview by ID
     */
    Interview getInterviewById(Long interviewId);

    /**
     * Fetch the interview and verify the caller owns it.
     * Throws FORBIDDEN otherwise. Use this in every controller endpoint
     * that mutates or reads interview state.
     */
    Interview assertOwnership(Long interviewId, Long userId);

    /**
     * Acquire a database row lock for report generation and verify ownership.
     * The caller must already run inside a transaction.
     */
    Interview lockForReport(Long interviewId, Long userId);

    /**
     * Persist a serialized report JSON onto the interview row and update
     * its final_score in one transaction.
     */
    Interview saveReport(Long interviewId, String reportJson, Integer overallScore);

    /**
     * Delete a user's interview and its message history.
     */
    void deleteInterview(Long userId, Long interviewId);
}
