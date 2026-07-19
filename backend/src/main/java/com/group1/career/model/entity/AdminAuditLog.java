package com.group1.career.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * F19: Admin audit log.
 *
 * <p>Written by the {@code @AuditLog} AOP aspect on every state-changing
 * admin endpoint. Captures who did what, to which entity, when and from
 * where, so PO can reconstruct the history of any moderation decision.</p>
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_audit_admin_created", columnList = "admin_id, created_at")
})
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Null after the administrator account itself is permanently erased.
     * The action/time are retained, while actor PII, IP, UA and snapshots are
     * stripped by the account-erasure workflow.
     */
    @Column(name = "admin_id")
    private Long adminId;

    /** Short action verb, e.g. BAN_USER, DELETE_QUESTION, REPLY_FEEDBACK. */
    @Column(name = "action", length = 60, nullable = false)
    private String action;

    /** Entity type, e.g. USER, QUESTION, FEEDBACK, CAREER_NODE. */
    @Column(name = "target_type", length = 40, nullable = false)
    private String targetType;

    /** String ID of the affected entity (flexible type). */
    @Column(name = "target_id", length = 40)
    private String targetId;

    /** JSON snapshot of the entity before the change (null for CREATE). */
    @Column(name = "before_json", columnDefinition = "json")
    private String beforeJson;

    /** JSON snapshot of the entity after the change (null for DELETE). */
    @Column(name = "after_json", columnDefinition = "json")
    private String afterJson;

    /** Remote IP address of the admin at the time of the action. */
    @Column(name = "ip", length = 45)
    private String ip;

    /** User-Agent header (for forensics). */
    @Column(name = "ua", length = 255)
    private String ua;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
