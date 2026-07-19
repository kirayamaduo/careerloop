package com.group1.career.interceptor;

import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.User;
import com.group1.career.repository.UserRepository;
import com.group1.career.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private static final Long USER_ID = 42L;
    private UserRepository userRepository;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtUtils.configure(
                "auth-interceptor-test-secret-at-least-thirty-two-bytes",
                60_000L);
        userRepository = mock(UserRepository.class);
        interceptor = new AuthInterceptor(userRepository);
    }

    @Test
    void validTokenWhoseSubjectNoLongerExistsIsUnauthorized() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        BizException error = assertThrows(
                BizException.class,
                () -> preHandle("/users/me", bearerToken()));

        assertEquals(ErrorCode.UNAUTHORIZED_ERROR.getCode(), error.getCode());
    }

    @Test
    void softDeletedAccountReturnsGoneOnOrdinaryEndpoint() {
        User user = activeUser();
        user.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        BizException error = assertThrows(
                BizException.class,
                () -> preHandle("/users/me", bearerToken()));

        assertEquals(ErrorCode.ACCOUNT_DELETED.getCode(), error.getCode());
    }

    @Test
    void softDeletedAccountCanReachOnlyExplicitCancellationEndpoint() throws Exception {
        User user = activeUser();
        user.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        MockHttpServletRequest request =
                preHandle("/users/me/cancel-deletion", bearerToken());

        assertEquals(USER_ID, request.getAttribute("userId"));
    }

    @Test
    void bannedAccountReturnsForbiddenEvenOnDeletionCancellationEndpoint() {
        User user = activeUser();
        user.setStatus(2);
        user.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        BizException error = assertThrows(
                BizException.class,
                () -> preHandle("/users/me/cancel-deletion", bearerToken()));

        assertEquals(ErrorCode.ACCOUNT_BANNED.getCode(), error.getCode());
        assertEquals(403, error.getCode());
    }

    @Test
    void invalidTokenIsRejectedBeforeDatabaseLookup() {
        BizException error = assertThrows(
                BizException.class,
                () -> preHandle("/users/me", "Bearer not-a-jwt"));

        assertEquals(ErrorCode.UNAUTHORIZED_ERROR.getCode(), error.getCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void activeAccountIsBoundToRequestIdentity() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));

        MockHttpServletRequest request = preHandle("/users/me", bearerToken());

        assertEquals(USER_ID, request.getAttribute("userId"));
    }

    @Test
    void tokenFromAnOlderAuthGenerationIsUnauthorized() {
        User user = activeUser();
        user.setAuthVersion(3L);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        BizException error = assertThrows(
                BizException.class,
                () -> preHandle(
                        "/users/me",
                        "Bearer " + JwtUtils.generateToken(USER_ID, "USER", 2L)));

        assertEquals(ErrorCode.UNAUTHORIZED_ERROR.getCode(), error.getCode());
    }

    @Test
    void tokenFromCurrentAuthGenerationIsAccepted() throws Exception {
        User user = activeUser();
        user.setAuthVersion(3L);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = preHandle(
                "/users/me",
                "Bearer " + JwtUtils.generateToken(USER_ID, "USER", 3L));

        assertEquals(USER_ID, request.getAttribute("userId"));
    }

    private MockHttpServletRequest preHandle(String uri, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("Authorization", authorization);
        boolean accepted = interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object());
        assertTrue(accepted);
        return request;
    }

    private String bearerToken() {
        return "Bearer " + JwtUtils.generateToken(USER_ID, "USER");
    }

    private User activeUser() {
        return User.builder().userId(USER_ID).status(1).build();
    }
}
