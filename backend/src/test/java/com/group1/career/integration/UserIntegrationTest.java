package com.group1.career.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.service.EmailService;
import com.group1.career.service.VerificationCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Spring Boot test of the registration / login flow against an
 * in-memory H2 schema. After Sprint A:
 *   - identityType uses {@code EMAIL_PASSWORD} (the older PASSWORD/MOBILE
 *     buckets were removed).
 *   - register requires a 6-digit code; we stub VerificationCodeService to
 *     accept any code so the test doesn't need a real SMTP round-trip.
 *   - EmailService is stubbed to a no-op so test runs don't try to dial
 *     QQ SMTP and fail in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VerificationCodeService verificationCodeService;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    public void stubEmailVerification() {
        // Treat every code we provide in the test as valid; we're testing
        // user / auth wiring, not the SMTP code-store flow itself.
        when(verificationCodeService.verify(anyString(), anyString(), anyString())).thenReturn(true);
        when(verificationCodeService.generateAndStore(anyString(), anyString())).thenReturn("123456");
    }

    private Map<String, String> registerPayload(String nickname, String email, String password) {
        Map<String, String> r = new HashMap<>();
        r.put("nickname", nickname);
        r.put("identityType", "EMAIL_PASSWORD");
        r.put("identifier", email);
        r.put("credential", password);
        r.put("code", "123456");
        return r;
    }

    @Test
    @DisplayName("Integration: register persists user and returns 200")
    public void testCompleteUserRegistrationFlow() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registerPayload("IntegrationTestUser", "integration-test@example.com", "test123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("IntegrationTestUser"));
    }

    @Test
    @DisplayName("Integration: register then login returns JWT + user payload")
    public void testLoginFlow() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registerPayload("LoginTestUser", "login-test@example.com", "password123"))))
                .andExpect(status().isOk());

        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("identityType", "EMAIL_PASSWORD");
        loginRequest.put("identifier", "login-test@example.com");
        loginRequest.put("credential", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.nickname").value("LoginTestUser"));
    }

    @Test
    @DisplayName("Integration: re-registering the same email surfaces a biz error")
    public void testDuplicateRegistrationPrevention() throws Exception {
        Map<String, String> request = registerPayload("DuplicateTestUser", "duplicate@example.com", "pass123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Re-registering with the same email returns a stable business error
        // so the UI can render a friendly message without a false system error.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4002));
    }
}
