package com.group1.career.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Performs one expired account erasure in an independent transaction.
 *
 * <p>The user row is locked and its grace-period timestamp is rechecked before
 * any irreversible action. OSS references are deleted first; a failed object
 * deletion aborts the database transaction so the job can retry. Database
 * deletion is child-first and explicit even where a newer schema also has
 * {@code ON DELETE CASCADE}, keeping upgraded and fresh installations safe.</p>
 */
@Slf4j
@Service
public class AccountDeletionService {

    static final List<String> CHILD_DELETE_SQL = List.of(
            "DELETE FROM assessment_answers WHERE record_id IN " +
                    "(SELECT record_id FROM assessment_records WHERE user_id = ?)",
            "DELETE FROM interview_messages WHERE interview_id IN " +
                    "(SELECT interview_id FROM interviews WHERE user_id = ?)",
            "DELETE FROM assistant_messages WHERE session_id IN " +
                    "(SELECT session_id FROM assistant_sessions WHERE user_id = ?)",
            "DELETE FROM resume_profile_keywords WHERE user_id = ? OR resume_id IN " +
                    "(SELECT resume_id FROM resumes WHERE user_id = ?)"
    );

    static final List<String> DIRECT_DELETE_SQL = List.of(
            "DELETE FROM agent_events WHERE user_id = ?",
            "DELETE FROM agent_states WHERE user_id = ?",
            "DELETE FROM agent_tasks WHERE user_id = ?",
            "DELETE FROM agent_user_profiles WHERE user_id = ?",
            "DELETE FROM check_ins WHERE user_id = ?",
            "DELETE FROM notifications WHERE user_id = ?",
            "DELETE FROM user_career_progress WHERE user_id = ?",
            "DELETE FROM user_career_plans WHERE user_id = ?",
            "DELETE FROM user_consents WHERE user_id = ?",
            "DELETE FROM user_facts WHERE user_id = ?",
            "DELETE FROM user_profile_tags WHERE user_id = ?",
            "DELETE FROM wx_subscribe_quota WHERE user_id = ?",
            "DELETE FROM user_feedback WHERE user_id = ?",
            "DELETE FROM assessment_records WHERE user_id = ?",
            "DELETE FROM interviews WHERE user_id = ?",
            "DELETE FROM resumes WHERE user_id = ?",
            "DELETE FROM conversation_summaries WHERE user_id = ?",
            "DELETE FROM assistant_sessions WHERE user_id = ?",
            "DELETE FROM user_roles WHERE user_id = ?",
            "DELETE FROM user_auths WHERE user_id = ?"
    );

    private final JdbcTemplate jdbcTemplate;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final String auditHashSecret;
    private final String questionBankPepper;

    public AccountDeletionService(
            JdbcTemplate jdbcTemplate,
            FileService fileService,
            ObjectMapper objectMapper,
            @Value("${account-deletion.audit-hash-secret:${jwt.secret}}") String auditHashSecret,
            @Value("${app.questionbank.pepper:careerloop-qbank}") String questionBankPepper) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
        this.auditHashSecret = auditHashSecret;
        this.questionBankPepper = questionBankPepper;
    }

    /**
     * @return true when the user was permanently erased; false when it no
     * longer exists or no longer qualifies (for example deletion was cancelled).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean hardDeleteExpiredUser(Long userId, LocalDateTime cutoff) {
        if (userId == null || cutoff == null) return false;

        List<UserPurgeRow> rows = jdbcTemplate.query(
                "SELECT deleted_at, avatar_url FROM users WHERE user_id = ? FOR UPDATE",
                (rs, rowNum) -> new UserPurgeRow(
                        rs.getTimestamp("deleted_at") == null
                                ? null
                                : rs.getTimestamp("deleted_at").toLocalDateTime(),
                        rs.getString("avatar_url")),
                userId);
        if (rows.isEmpty()) return false;

        UserPurgeRow user = rows.get(0);
        if (user.deletedAt() == null || user.deletedAt().isAfter(cutoff)) {
            return false;
        }

        Set<String> objectKeys = collectObjectKeys(userId, user.avatarUrl());
        for (String key : objectKeys) {
            if (!fileService.deleteObject(key)) {
                throw new IllegalStateException("OSS erasure failed; database purge deferred");
            }
        }
        if (!fileService.deletePrefix("tts/" + userId + "/")) {
            throw new IllegalStateException("OSS TTS erasure failed; database purge deferred");
        }

        // Delete nested children before their owning rows.
        jdbcTemplate.update(CHILD_DELETE_SQL.get(0), userId);
        jdbcTemplate.update(CHILD_DELETE_SQL.get(1), userId);
        jdbcTemplate.update(CHILD_DELETE_SQL.get(2), userId);
        jdbcTemplate.update(CHILD_DELETE_SQL.get(3), userId, userId);
        for (String sql : DIRECT_DELETE_SQL) {
            jdbcTemplate.update(sql, userId);
        }

        // Retain non-identifying aggregate/audit facts only.
        jdbcTemplate.update(
                "UPDATE usage_events SET user_id = NULL, payload = NULL WHERE user_id = ?",
                userId);
        jdbcTemplate.update(
                "UPDATE interview_questions SET contributor_hash = NULL " +
                        "WHERE contributor_hash = ? OR contributor_hash = ?",
                questionContributorHash(userId),
                "uid-" + userId);
        jdbcTemplate.update(
                "UPDATE admin_audit_log SET admin_id = NULL, before_json = NULL, after_json = NULL, " +
                        "ip = NULL, ua = NULL WHERE admin_id = ?",
                userId);
        jdbcTemplate.update(
                "UPDATE admin_audit_log SET target_id = NULL, before_json = NULL, after_json = NULL " +
                        "WHERE target_type = 'USER' AND target_id = ?",
                String.valueOf(userId));
        // Preserve cancelled attempts as CANCELLED. Only the currently pending
        // request becomes COMPLETED; every historical row is then detached
        // from the raw user id and network fingerprint.
        jdbcTemplate.update(
                "UPDATE account_deletion_log SET status = 'COMPLETED', completed_at = ? " +
                        "WHERE user_id = ? AND status = 'PENDING'",
                Timestamp.valueOf(LocalDateTime.now()),
                userId);
        jdbcTemplate.update(
                "UPDATE account_deletion_log SET subject_hash = ?, user_id = NULL, ip_hash = NULL " +
                        "WHERE user_id = ?",
                auditSubjectHash(userId),
                userId);

        int deleted = jdbcTemplate.update(
                "DELETE FROM users WHERE user_id = ? AND deleted_at <= ?",
                userId,
                Timestamp.valueOf(cutoff));
        if (deleted != 1) {
            throw new IllegalStateException("User changed during account erasure");
        }
        return true;
    }

    private Set<String> collectObjectKeys(Long userId, String avatarUrl) {
        Set<String> keys = new LinkedHashSet<>();
        addIfPresent(keys, avatarUrl);

        List<String> resumeKeys = jdbcTemplate.query(
                "SELECT file_url FROM resumes WHERE user_id = ?",
                (rs, rowNum) -> rs.getString(1),
                userId);
        resumeKeys.forEach(key -> addIfPresent(keys, key));

        List<String> attachmentJsonRows = jdbcTemplate.query(
                "SELECT attachment_urls FROM user_feedback " +
                        "WHERE user_id = ? AND attachment_urls IS NOT NULL",
                (rs, rowNum) -> rs.getString(1),
                userId);
        for (String raw : attachmentJsonRows) {
            parseAttachmentKeys(raw).forEach(key -> addIfPresent(keys, key));
        }
        return keys;
    }

    private List<String> parseAttachmentKeys(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(raw);
            List<String> keys = new ArrayList<>();
            if (root.isTextual()) {
                keys.add(root.asText());
            } else if (root.isArray()) {
                for (JsonNode node : root) {
                    if (!node.isTextual()) {
                        throw new IllegalStateException("Non-text feedback attachment reference");
                    }
                    keys.add(node.asText());
                }
            } else {
                throw new IllegalStateException("Invalid feedback attachment JSON shape");
            }
            return keys;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot safely enumerate feedback attachments; database purge deferred", e);
        }
    }

    private void addIfPresent(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) keys.add(key.trim());
    }

    private String auditSubjectHash(Long userId) {
        if (auditHashSecret == null || auditHashSecret.isBlank()) {
            throw new IllegalStateException("Account-deletion audit hash secret is missing");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    auditHashSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(("account-erasure:" + userId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create anonymous deletion audit token", e);
        }
    }

    private String questionContributorHash(Long userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (questionBankPepper + ":" + userId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static record UserPurgeRow(LocalDateTime deletedAt, String avatarUrl) {}
}
