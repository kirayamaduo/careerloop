package com.group1.career.service.impl;

import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.AccountDeletionLog;
import com.group1.career.model.entity.User;
import com.group1.career.model.entity.UserAuth;
import com.group1.career.repository.AccountDeletionLogRepository;
import com.group1.career.repository.RoleRepository;
import com.group1.career.repository.UserAuthRepository;
import com.group1.career.repository.UserRepository;
import com.group1.career.repository.UserRoleRepository;
import com.group1.career.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceRecoveryTest {

    private UserRepository userRepository;
    private UserAuthRepository authRepository;
    private PasswordEncoder passwordEncoder;
    private AccountDeletionLogRepository deletionLogRepository;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authRepository = mock(UserAuthRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        deletionLogRepository = mock(AccountDeletionLogRepository.class);
        service = spy(new UserServiceImpl(
                userRepository,
                authRepository,
                mock(RoleRepository.class),
                mock(UserRoleRepository.class),
                passwordEncoder,
                mock(FileService.class),
                deletionLogRepository));
        ReflectionTestUtils.setField(service, "wechatAppId", "test-appid");
        ReflectionTestUtils.setField(service, "wechatSecret", "test-secret");
    }

    @Test
    @DisplayName("Verified email login automatically restores account inside 30-day grace period")
    void emailLoginRestoresWithinGracePeriod() {
        User user = pendingUser(7L, 10);
        UserAuth auth = emailAuth(user.getUserId());
        AccountDeletionLog logRow = pendingLog(user.getUserId());
        when(authRepository.findByIdentifierAndIdentityType(
                "student@example.com", "EMAIL_PASSWORD")).thenReturn(Optional.of(auth));
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(deletionLogRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                7L, "PENDING")).thenReturn(Optional.of(logRow));

        User restored = service.login(
                "EMAIL_PASSWORD", "Student@Example.com", "correct-password");

        assertNull(restored.getDeletedAt());
        assertTrue(Boolean.TRUE.equals(restored.getAccountRestored()));
        assertEquals("CANCELLED", logRow.getStatus());
        assertNotNull(logRow.getCancelledAt());
        verify(userRepository).save(user);
        verify(deletionLogRepository).save(logRow);
        verify(authRepository).save(auth);
    }

    @Test
    @DisplayName("Verified WeChat identity restores the same account inside grace period")
    void wechatLoginRestoresWithinGracePeriod() {
        User user = pendingUser(8L, 5);
        UserAuth auth = UserAuth.builder()
                .userId(8L)
                .identityType("WECHAT")
                .identifier("openid-8")
                .credential("")
                .build();
        AccountDeletionLog logRow = pendingLog(8L);
        doReturn(Map.of("openid", "openid-8"))
                .when(service).fetchWechatSession(anyString());
        when(authRepository.findByIdentifierAndIdentityType("openid-8", "WECHAT"))
                .thenReturn(Optional.of(auth));
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(deletionLogRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                8L, "PENDING")).thenReturn(Optional.of(logRow));

        User restored = service.wechatLogin("verified-code");

        assertNull(restored.getDeletedAt());
        assertTrue(Boolean.TRUE.equals(restored.getAccountRestored()));
        assertEquals("CANCELLED", logRow.getStatus());
        assertNotNull(logRow.getCancelledAt());
        verify(userRepository).save(user);
        verify(deletionLogRepository).save(logRow);
    }

    @Test
    @DisplayName("Expired grace period cannot be recovered even with correct password")
    void expiredEmailAccountCannotRecover() {
        User user = pendingUser(7L, 31);
        UserAuth auth = emailAuth(7L);
        when(authRepository.findByIdentifierAndIdentityType(
                "student@example.com", "EMAIL_PASSWORD")).thenReturn(Optional.of(auth));
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        BizException error = assertThrows(BizException.class, () ->
                service.login("EMAIL_PASSWORD", "student@example.com", "correct-password"));

        assertEquals(ErrorCode.ACCOUNT_DELETED.getCode(), error.getCode());
        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("Expired grace period cannot be recovered with a verified WeChat identity")
    void expiredWechatAccountCannotRecover() {
        User user = pendingUser(8L, 31);
        UserAuth auth = UserAuth.builder()
                .userId(8L)
                .identityType("WECHAT")
                .identifier("openid-8")
                .credential("")
                .build();
        doReturn(Map.of("openid", "openid-8"))
                .when(service).fetchWechatSession(anyString());
        when(authRepository.findByIdentifierAndIdentityType("openid-8", "WECHAT"))
                .thenReturn(Optional.of(auth));
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));

        BizException error = assertThrows(
                BizException.class, () -> service.wechatLogin("verified-code"));

        assertEquals(ErrorCode.ACCOUNT_DELETED.getCode(), error.getCode());
        verify(userRepository, never()).save(user);
        verify(authRepository, never()).save(auth);
    }

    @Test
    @DisplayName("Banned account cannot use deletion recovery to reactivate")
    void bannedAccountCannotBypassStatusCheck() {
        User user = pendingUser(7L, 5);
        user.setStatus(2);
        UserAuth auth = emailAuth(7L);
        when(authRepository.findByIdentifierAndIdentityType(
                "student@example.com", "EMAIL_PASSWORD")).thenReturn(Optional.of(auth));
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        BizException error = assertThrows(BizException.class, () ->
                service.login("EMAIL_PASSWORD", "student@example.com", "correct-password"));

        assertEquals(ErrorCode.ACCOUNT_BANNED.getCode(), error.getCode());
        assertFalse(Boolean.TRUE.equals(user.getAccountRestored()));
        verify(userRepository, never()).save(user);
        verify(deletionLogRepository, never())
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(7L, "PENDING");
    }

    @Test
    @DisplayName("Banned WeChat account cannot use deletion recovery to reactivate")
    void bannedWechatAccountCannotBypassStatusCheck() {
        User user = pendingUser(8L, 5);
        user.setStatus(2);
        UserAuth auth = UserAuth.builder()
                .userId(8L)
                .identityType("WECHAT")
                .identifier("openid-8")
                .credential("")
                .build();
        doReturn(Map.of("openid", "openid-8"))
                .when(service).fetchWechatSession(anyString());
        when(authRepository.findByIdentifierAndIdentityType("openid-8", "WECHAT"))
                .thenReturn(Optional.of(auth));
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));

        BizException error = assertThrows(
                BizException.class, () -> service.wechatLogin("verified-code"));

        assertEquals(ErrorCode.ACCOUNT_BANNED.getCode(), error.getCode());
        assertFalse(Boolean.TRUE.equals(user.getAccountRestored()));
        verify(userRepository, never()).save(user);
        verify(authRepository, never()).save(auth);
    }

    @Test
    @DisplayName("Wrong password never reaches the account recovery path")
    void wrongPasswordCannotCancelDeletion() {
        UserAuth auth = emailAuth(7L);
        when(authRepository.findByIdentifierAndIdentityType(
                "student@example.com", "EMAIL_PASSWORD")).thenReturn(Optional.of(auth));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        BizException error = assertThrows(BizException.class, () ->
                service.login("EMAIL_PASSWORD", "student@example.com", "wrong-password"));
        assertEquals(401, error.getCode());
        assertEquals("邮箱或密码错误", error.getMessage());

        verify(userRepository, never()).findById(7L);
        verify(deletionLogRepository, never())
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(7L, "PENDING");
    }

    @Test
    @DisplayName("Unknown email and wrong password use the same public login error")
    void unknownEmailUsesGenericCredentialError() {
        when(authRepository.findByIdentifierAndIdentityType(
                "missing@example.com", "EMAIL_PASSWORD")).thenReturn(Optional.empty());

        BizException error = assertThrows(BizException.class, () ->
                service.login("EMAIL_PASSWORD", "Missing@Example.com", "any-password"));

        assertEquals(401, error.getCode());
        assertEquals("邮箱或密码错误", error.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Password reset revokes every previously issued token")
    void passwordResetBumpsAuthGeneration() {
        UserAuth auth = emailAuth(7L);
        when(authRepository.findByIdentifierAndIdentityType(
                "student@example.com", "EMAIL_PASSWORD")).thenReturn(Optional.of(auth));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.incrementAuthVersion(7L)).thenReturn(1);

        service.resetPassword("Student@Example.com", "new-password");

        assertEquals("new-hash", auth.getCredential());
        verify(authRepository).save(auth);
        verify(userRepository).incrementAuthVersion(7L);
    }

    @Test
    @DisplayName("Authenticated password change verifies old password and revokes sessions")
    void passwordChangeBumpsAuthGeneration() {
        UserAuth auth = emailAuth(7L);
        when(authRepository.findByUserId(7L)).thenReturn(List.of(auth));
        when(passwordEncoder.matches("old-password", "hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.incrementAuthVersion(7L)).thenReturn(1);

        service.changePassword(7L, "old-password", "new-password");

        assertEquals("new-hash", auth.getCredential());
        verify(authRepository).save(auth);
        verify(userRepository).incrementAuthVersion(7L);
    }

    @Test
    @DisplayName("Wrong current password does not alter credentials or revoke sessions")
    void wrongCurrentPasswordDoesNotMutateSecurityState() {
        UserAuth auth = emailAuth(7L);
        when(authRepository.findByUserId(7L)).thenReturn(List.of(auth));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        BizException error = assertThrows(BizException.class,
                () -> service.changePassword(7L, "wrong-password", "new-password"));

        assertEquals(400, error.getCode());
        assertEquals("hash", auth.getCredential());
        verify(authRepository, never()).save(auth);
        verify(userRepository, never()).incrementAuthVersion(7L);
    }

    @Test
    @DisplayName("Logout performs an atomic auth-generation bump")
    void logoutRevokesSessionsAtomically() {
        when(userRepository.incrementAuthVersion(7L)).thenReturn(1);

        service.revokeSessions(7L);

        verify(userRepository).incrementAuthVersion(7L);
    }

    private User pendingUser(long userId, int daysAgo) {
        return User.builder()
                .userId(userId)
                .status(1)
                .deletedAt(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    private UserAuth emailAuth(long userId) {
        return UserAuth.builder()
                .userId(userId)
                .identityType("EMAIL_PASSWORD")
                .identifier("student@example.com")
                .credential("hash")
                .build();
    }

    private AccountDeletionLog pendingLog(long userId) {
        return AccountDeletionLog.builder()
                .userId(userId)
                .status("PENDING")
                .build();
    }
}
