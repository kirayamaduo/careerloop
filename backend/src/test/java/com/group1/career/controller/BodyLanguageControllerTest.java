package com.group1.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.service.BodyLanguageService;
import com.group1.career.service.InterviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BodyLanguageController.class)
class BodyLanguageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterviewService interviewService;

    @MockitoBean
    private BodyLanguageService bodyLanguageService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @Test
    @DisplayName("Frame ownership and rate-limit identity both come from JWT")
    void frameUsesAuthenticatedUserAndMapsRateLimit() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(bodyLanguageService.recordFrame(7L, 9L, "base64"))
                .thenReturn(BodyLanguageService.SubmissionResult.RATE_LIMITED);

        mockMvc.perform(post("/api/body-language/frame")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("interviewId", 9, "frameBase64", "base64"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429));

        verify(interviewService).assertOwnership(9L, 7L);
        verify(bodyLanguageService).recordFrame(7L, 9L, "base64");
    }

    @Test
    @DisplayName("Missing frame is rejected before ownership or sidecar work")
    void missingFrameIsRejected() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/body-language/frame")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("interviewId", 9))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
