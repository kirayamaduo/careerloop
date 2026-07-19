package com.group1.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.common.ErrorCode;
import com.group1.career.config.JwtConfig;
import com.group1.career.exception.BizException;
import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.model.entity.User;
import com.group1.career.service.AuthRateLimitService;
import com.group1.career.service.EmailService;
import com.group1.career.service.UserService;
import com.group1.career.service.VerificationCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth contract tests after the Sprint A overhaul:
 *   - identityType is now {@code EMAIL_PASSWORD} (the previous {@code EMAIL}
 *     / {@code MOBILE} buckets were collapsed since we only support email).
 *   - register requires a 6-digit verification code; the test stubs it
 *     accepted via {@link VerificationCodeService#verify}.
 *   - the controller now wires {@link EmailService} +
 *     {@link VerificationCodeService} alongside {@link UserService}; both
 *     are mocked so the WebMvc context loads.
 */
@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import(JwtConfig.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private VerificationCodeService verificationCodeService;

    @MockitoBean
    private AuthRateLimitService authRateLimitService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== Verification code delivery =====

    @Test
    @DisplayName("POST /auth/send-code — only accepts the two supported purposes")
    void testSendCode_RejectsUnknownPurpose() throws Exception {
        mockMvc.perform(post("/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "purpose": "UNBOUNDED_KEY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("POST /auth/send-code — SMTP failure releases the resend cooldown")
    void testSendCode_DeliveryFailureInvalidatesGeneratedCode() throws Exception {
        when(verificationCodeService.generateAndStore("alice@example.com", "REGISTER"))
                .thenReturn("123456");
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService)
                .sendVerificationCode("alice@example.com", "123456", "REGISTER");

        mockMvc.perform(post("/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "purpose": "REGISTER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("验证码发送失败，请稍后重试"));

        verify(verificationCodeService).invalidate("alice@example.com", "REGISTER");
    }

    @Test
    @DisplayName("POST /auth/send-code — normalizes email before limiting, storage and delivery")
    void testSendCode_NormalizesEmail() throws Exception {
        when(verificationCodeService.generateAndStore("alice@example.com", "REGISTER"))
                .thenReturn("123456");

        mockMvc.perform(post("/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Alice@Example.COM",
                                  "purpose": "REGISTER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("验证码已发送"));

        verify(authRateLimitService).check(
                eq(AuthRateLimitService.Operation.SEND_CODE),
                any(HttpServletRequest.class),
                eq("alice@example.com"));
        verify(emailService).sendVerificationCode(
                "alice@example.com", "123456", "REGISTER");
    }

    // ===== Register =====

    @Test
    @DisplayName("POST /auth/register — happy path with valid 6-digit code")
    public void testRegister_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("nickname", "Alice");
        request.put("identityType", "EMAIL_PASSWORD");
        request.put("identifier", "alice@example.com");
        request.put("credential", "pass123");
        request.put("code", "123456");

        when(verificationCodeService.verify(anyString(), anyString(), anyString())).thenReturn(true);

        User mockUser = User.builder().userId(1L).nickname("Alice").build();
        when(userService.register(anyString(), anyString(), anyString(), anyString())).thenReturn(mockUser);
        when(userService.hydrateUrl(mockUser)).thenReturn(mockUser);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.nickname").value("Alice"));
    }

    @Test
    @DisplayName("POST /auth/register — bad code is rejected before user touches DB")
    public void testRegister_BadCode() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("nickname", "Alice");
        request.put("identityType", "EMAIL_PASSWORD");
        request.put("identifier", "alice@example.com");
        request.put("credential", "pass123");
        request.put("code", "999999");

        when(verificationCodeService.verify(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /auth/register — nickname under 2 chars fails Bean Validation")
    public void testRegister_NicknameTooShort() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("nickname", "A");
        request.put("identityType", "EMAIL_PASSWORD");
        request.put("identifier", "alice@example.com");
        request.put("credential", "pass123");
        request.put("code", "123456");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===== Login =====

    @Test
    @DisplayName("POST /auth/login — returns JWT + user payload")
    public void testLogin_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("identityType", "EMAIL_PASSWORD");
        request.put("identifier", "alice@example.com");
        request.put("credential", "password123");

        User mockUser = User.builder().userId(1L).nickname("Alice").build();
        when(userService.login(anyString(), anyString(), anyString())).thenReturn(mockUser);
        when(userService.hydrateUrl(mockUser)).thenReturn(mockUser);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.user.userId").value(1));
    }

    @Test
    @DisplayName("POST /auth/login — surfaces user-not-found via biz code")
    public void testLogin_UserNotFound() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("identityType", "EMAIL_PASSWORD");
        request.put("identifier", "nobody@example.com");
        request.put("credential", "wrongpass");

        when(userService.login(anyString(), anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("POST /auth/login — missing credential is a Bean Validation 400")
    public void testLogin_MissingCredential() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("identityType", "EMAIL_PASSWORD");
        request.put("identifier", "test@example.com");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login — a rate-limit rejection stops password verification")
    void testLogin_RateLimitedBeforeUserService() throws Exception {
        doThrow(new BizException(429, "请求过于频繁，请稍后重试"))
                .when(authRateLimitService)
                .check(eq(AuthRateLimitService.Operation.LOGIN),
                        any(HttpServletRequest.class), eq("alice@example.com"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identityType": "EMAIL_PASSWORD",
                                  "identifier": "Alice@Example.COM",
                                  "credential": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429));

        verify(userService, never()).login(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /auth/logout — revokes the authenticated user's sessions")
    void logoutRevokesServerSideSessions() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        mockMvc.perform(post("/auth/logout").requestAttr("userId", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).revokeSessions(9L);
    }

    @Test
    @DisplayName("POST /auth/change-password — changes credential for authenticated user")
    void changePasswordUsesAuthenticatedIdentity() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        mockMvc.perform(post("/auth/change-password")
                        .requestAttr("userId", 9L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "old-password",
                                  "newPassword": "new-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).changePassword(9L, "old-password", "new-password");
    }
}
