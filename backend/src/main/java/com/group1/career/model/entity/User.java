package com.group1.career.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "nickname", length = 64)
    private String nickname;

    /**
     * OSS object key for the user's avatar, e.g. {@code avatars/uuid.jpg}.
     * Historically held a full URL; now stores only the key so the same row
     * survives bucket / endpoint changes. Use {@link #avatarViewUrl} on the
     * client side.
     */
    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * Short-lived signed avatar URL populated by {@code UserService.hydrateUrl}
     * before serialization; never persisted.
     */
    @Transient
    private String avatarViewUrl;

    /**
     * Response-only flag set when a verified login automatically restores an
     * account during its 30-day deletion grace period.
     */
    @Transient
    @Builder.Default
    private Boolean accountRestored = false;

    @Column(name = "school", length = 100)
    private String school;

    @Column(name = "major", length = 100)
    private String major;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "points")
    @Builder.Default
    private Integer points = 0;

    @Column(name = "is_vip")
    @Builder.Default
    private Boolean isVip = false;

    @Column(name = "status")
    @Builder.Default
    private Integer status = 1;

    /**
     * Server-side session generation. Every security-sensitive account event
     * increments this value; JWTs carrying an older generation are rejected.
     * It is deliberately excluded from API responses because it is internal
     * authentication state, not profile data.
     */
    @JsonIgnore
    @Column(name = "auth_version", nullable = false)
    @Builder.Default
    private Long authVersion = 0L;

    /**
     * Soft FK to {@code organizations.org_id} (Sprint D-4). Null for the
     * default C-side users; set when an admin invites a student into a
     * cohort. Never enforced at the DB layer to keep the migration painless.
     */
    @Column(name = "org_id")
    private Long orgId;

    /**
     * Cross-tool user portrait, JSON-serialized {@code UserProfileSnapshot}.
     * Written when a user finishes an assessment / uploads a resume /
     * completes an interview, then read back by the AI assistant + interview
     * start page + resume diagnosis page so each tool inherits what every
     * other tool already learned. Free-form JSON so we don't have to migrate
     * the table every time we want to remember something new.
     */
    @Column(name = "profile_snapshot", columnDefinition = "json")
    private String profileSnapshot;

    /**
     * F25: soft-delete timestamp. Non-null means the user has requested
     * account deletion; a nightly job hard-deletes rows older than 30 days.
     * All auth endpoints reject users with a non-null deleted_at.
     */
    @Column(name = "deleted_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedAt;

    /**
     * F16: set by admin when status is changed to BANNED.
     * Shown to the user when they attempt to log in.
     */
    @Column(name = "banned_reason", length = 255)
    private String bannedReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
