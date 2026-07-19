package com.group1.career.model;

/**
 * F9: Canonical notification type constants.
 *
 * <p>The {@code Notification.type} column is a VARCHAR(50); we use these
 * string constants everywhere instead of a JPA {@code @Enumerated} field so
 * that old rows with different strings don't break deserialization.</p>
 */
public final class NotificationTypes {

    private NotificationTypes() {}

    /** Generic system announcement or platform notice. */
    public static final String SYSTEM            = "SYSTEM";

    /** Weekly career report generated and ready to view. */
    public static final String WEEKLY_REPORT     = "WEEKLY_REPORT";

    /** Mock interview session completed — report is available. */
    public static final String INTERVIEW_REPORT  = "INTERVIEW_REPORT";

    /** Competency assessment completed — results are available. */
    public static final String ASSESSMENT_RESULT = "ASSESSMENT_RESULT";

    /** AI resume diagnosis completed — feedback is available. */
    public static final String RESUME_DIAGNOSIS  = "RESUME_DIAGNOSIS";

    /** Check-in streak about to break — reminder to check in today. */
    public static final String STREAK_WARNING    = "STREAK_WARNING";

    /** Someone liked the user's market post / shared resource. */
    public static final String MARKET_LIKE       = "MARKET_LIKE";

    /** AI-initiated proactive message (F14). */
    public static final String AI_PROACTIVE      = "AI_PROACTIVE";

    /** Broadcast pushed by an admin to a user or org segment. */
    public static final String ADMIN_BROADCAST   = "ADMIN_BROADCAST";

    /** Action assigned by the connected school employment platform. */
    public static final String SCHOOL_INTERVENTION = "SCHOOL_INTERVENTION";
}
