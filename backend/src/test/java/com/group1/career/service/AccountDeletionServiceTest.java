package com.group1.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {

    private FakeJdbcTemplate jdbc;
    private FileService fileService;
    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        jdbc = new FakeJdbcTemplate();
        fileService = mock(FileService.class);
        service = new AccountDeletionService(
                jdbc,
                fileService,
                new ObjectMapper(),
                "test-audit-secret-at-least-32-bytes",
                "test-question-pepper");
        when(fileService.deletePrefix(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Expired account deletes all referenced OSS objects and every user data family")
    void expiredAccountIsCompletelyPurged() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        jdbc.userRow = new AccountDeletionService.UserPurgeRow(
                cutoff.minusMinutes(1), "avatars/user.jpg");
        jdbc.resumeKeys = List.of("resumes/a.pdf", "resumes/b.pdf");
        jdbc.feedbackAttachments = List.of(
                "[\"feedback/a.png\",\"feedback/b.png\",\"resumes/a.pdf\"]");
        when(fileService.deleteObject(anyString())).thenReturn(true);

        assertTrue(service.hardDeleteExpiredUser(7L, cutoff));

        for (String key : List.of(
                "avatars/user.jpg", "resumes/a.pdf", "resumes/b.pdf",
                "feedback/a.png", "feedback/b.png")) {
            verify(fileService).deleteObject(key);
        }
        verify(fileService).deletePrefix("tts/7/");

        String executed = String.join("\n", jdbc.updates.stream().map(Update::sql).toList());
        for (String table : List.of(
                "assessment_answers", "assessment_records",
                "interview_messages", "interviews",
                "assistant_messages", "assistant_sessions",
                "resume_profile_keywords", "resumes",
                "agent_events", "agent_states", "agent_tasks", "agent_user_profiles",
                "check_ins", "notifications", "user_career_progress",
                "user_career_plans", "user_consents", "user_facts",
                "user_profile_tags", "wx_subscribe_quota", "user_feedback",
                "conversation_summaries", "user_roles", "user_auths",
                "usage_events", "interview_questions", "admin_audit_log",
                "account_deletion_log", "users")) {
            assertTrue(executed.contains(table), "missing purge/anonymization for " + table);
        }
        assertTrue(jdbc.updates.get(jdbc.updates.size() - 1).sql().startsWith("DELETE FROM users"),
                "users row must be deleted last");
        assertTrue(executed.contains(
                        "WHERE user_id = ? AND status = 'PENDING'"),
                "only a pending deletion request should become completed");
        assertTrue(executed.contains(
                        "SET subject_hash = ?, user_id = NULL, ip_hash = NULL"),
                "all historical deletion rows must lose their raw subject and IP");
        assertFalse(executed.contains(
                        "status = 'COMPLETED', completed_at = ? WHERE user_id = ?\n"),
                "cancelled history must not be relabelled as completed");
    }

    @Test
    @DisplayName("Any OSS failure keeps all database rows for a safe retry")
    void ossFailureAbortsBeforeDatabaseMutation() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        jdbc.userRow = new AccountDeletionService.UserPurgeRow(
                cutoff.minusMinutes(1), "avatars/user.jpg");
        when(fileService.deleteObject("avatars/user.jpg")).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.hardDeleteExpiredUser(7L, cutoff));
        assertTrue(jdbc.updates.isEmpty());
    }

    @Test
    @DisplayName("TTS namespace failure also defers all database erasure")
    void ttsPrefixFailureAbortsBeforeDatabaseMutation() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        jdbc.userRow = new AccountDeletionService.UserPurgeRow(
                cutoff.minusMinutes(1), null);
        when(fileService.deletePrefix("tts/7/")).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.hardDeleteExpiredUser(7L, cutoff));
        assertTrue(jdbc.updates.isEmpty());
    }

    @Test
    @DisplayName("Cancellation race is rechecked under row lock before deleting files")
    void noLongerExpiredUserIsSkipped() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        jdbc.userRow = new AccountDeletionService.UserPurgeRow(
                cutoff.plusSeconds(1), "avatars/user.jpg");

        assertFalse(service.hardDeleteExpiredUser(7L, cutoff));
        verify(fileService, never()).deleteObject(anyString());
        assertTrue(jdbc.updates.isEmpty());
    }

    @Test
    @DisplayName("Each account purge declares an independent REQUIRES_NEW transaction")
    void purgeUsesIndependentTransaction() throws Exception {
        Method method = AccountDeletionService.class.getMethod(
                "hardDeleteExpiredUser", Long.class, LocalDateTime.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private static final class FakeJdbcTemplate extends JdbcTemplate {
        private AccountDeletionService.UserPurgeRow userRow;
        private List<String> resumeKeys = List.of();
        private List<String> feedbackAttachments = List.of();
        private final List<Update> updates = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.startsWith("SELECT deleted_at")) {
                return userRow == null ? List.of() : (List<T>) List.of(userRow);
            }
            if (sql.startsWith("SELECT file_url")) {
                return (List<T>) resumeKeys;
            }
            if (sql.startsWith("SELECT attachment_urls")) {
                return (List<T>) feedbackAttachments;
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(new Update(sql, Arrays.asList(args)));
            return 1;
        }
    }

    private record Update(String sql, List<Object> args) {}
}
