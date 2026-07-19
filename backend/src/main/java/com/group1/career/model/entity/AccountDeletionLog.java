package com.group1.career.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * F25: Minimal account deletion audit log.
 *
 * <p>Before the grace period expires, {@code userId}/{@code ipHash} support
 * cancellation and abuse triage. At permanent erasure they are removed and
 * only a keyed, non-reversible subject hash plus request/completion timestamps
 * remain as the minimum compliance evidence.</p>
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "account_deletion_log")
public class AccountDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** SHA-256 of the remote IP address. Not the raw IP — no PII stored here. */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    /** HMAC-SHA256(userId), populated only when permanent erasure completes. */
    @Column(name = "subject_hash", length = 64)
    private String subjectHash;

    /** PENDING during grace period; CANCELLED on restore; COMPLETED on purge. */
    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "cancelled_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelledAt;

    /** Timestamp at which all directly linked personal data was purged. */
    @Column(name = "completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
