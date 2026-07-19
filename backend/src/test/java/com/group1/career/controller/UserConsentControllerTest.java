package com.group1.career.controller;

import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.model.entity.UserConsent;
import com.group1.career.repository.UserConsentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserConsentController.class)
class UserConsentControllerTest {

    private static final long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserConsentRepository consentRepository;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void authenticate() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", USER_ID);
            return true;
        });
    }

    @Test
    void recordsOnlyTheCurrentAgreementVersion() throws Exception {
        when(consentRepository.existsByUserIdAndAgreementVersion(
                USER_ID, UserConsentController.CURRENT_VERSION)).thenReturn(false);

        mockMvc.perform(post("/api/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agreementVersion": "1.1",
                                  "platform": "miniprogram"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("recorded"));

        verify(consentRepository).save(argThat(consent ->
                consent.getUserId().equals(USER_ID)
                        && "1.1".equals(consent.getAgreementVersion())
                        && "miniprogram".equals(consent.getPlatform())));
    }

    @Test
    void rejectsForgedOrObsoleteAgreementVersion() throws Exception {
        mockMvc.perform(post("/api/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agreementVersion": "999.0",
                                  "platform": "miniprogram"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(consentRepository, never()).save(any(UserConsent.class));
    }

    @Test
    void rejectsUnknownPlatformMetadata() throws Exception {
        mockMvc.perform(post("/api/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agreementVersion": "1.1",
                                  "platform": "forged-client"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(consentRepository, never()).save(any(UserConsent.class));
    }
}
