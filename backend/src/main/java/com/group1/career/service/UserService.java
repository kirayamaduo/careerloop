package com.group1.career.service;

import com.group1.career.model.entity.User;

public interface UserService {
    User register(String nickname, String identityType, String identifier, String credential);
    User login(String identityType, String identifier, String credential);
    User wechatLogin(String code);
    User getUserById(Long userId);

    /**
     * Patch user profile. Any non-null arg overrides the current value;
     * pass {@code null} for fields that should stay untouched.
     * @param avatarUrl OSS object key (e.g. {@code avatars/uuid.jpg}); the
     *                  field is named {@code avatarUrl} for column-name parity.
     * @param nickname  display name
     */
    User updateUser(Long userId, String nickname, String avatarUrl,
                    String school, String major, Integer graduationYear,
                    boolean clearGraduationYear);

    void resetPassword(String identifier, String newCredential);
    void changePassword(Long userId, String currentCredential, String newCredential);
    /** Invalidate every JWT issued with the user's current auth generation. */
    void revokeSessions(Long userId);
    boolean isEmailRegistered(String email);

    /**
     * Populate the transient {@code avatarViewUrl} with a short-lived signed
     * URL. Returns the same instance for chaining; mutates in place. Safe with
     * null input.
     */
    User hydrateUrl(User user);

    /**
     * F25: Mark account for deletion (30-day grace period).
     * Sets deleted_at = now(). A nightly job hard-deletes rows older than 30 days.
     * @param ipHash SHA-256 of the caller's IP for the audit log.
     */
    void requestDeletion(Long userId, String ipHash);

    /**
     * F25: Cancel a pending deletion request (within the 30-day grace period).
     * Clears deleted_at.
     */
    void cancelDeletion(Long userId);
}
