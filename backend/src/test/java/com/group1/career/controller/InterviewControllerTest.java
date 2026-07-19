package com.group1.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.model.entity.Interview;
import com.group1.career.model.entity.InterviewMessage;
import com.group1.career.service.AiService;
import com.group1.career.service.FileService;
import com.group1.career.service.InterviewService;
import com.group1.career.service.QuestionBankService;
import com.group1.career.service.VoiceService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
public class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewService interviewService;

    @MockitoBean
    private AiService aiService;

    /** Sprint B added voice / file dependencies on the controller; the
     *  existing tests don't exercise the voice path so they're left as
     *  no-op mocks. The dedicated voice-turn tests live alongside the
     *  service unit tests, not here. */
    @MockitoBean
    private VoiceService voiceService;

    @MockitoBean
    private FileService fileService;

    /** Phase 4: greeting flow can pull from the crowd-sourced bank with 50% probability;
     *  every test path stays on the generic angle so we leave it unstubbed. */
    @MockitoBean
    private QuestionBankService questionBankService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    /** UID we pretend the AuthInterceptor put into the request after JWT
     *  validation. Every controller method calls SecurityUtil under the
     *  hood, so without this stub the controller short-circuits with
     *  "Not authenticated" and every assertion below 401's. */
    private static final Long TEST_UID = 1L;

    @BeforeEach
    public void bypassAuth() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenAnswer(inv -> {
            HttpServletRequest req = inv.getArgument(0);
            req.setAttribute("userId", TEST_UID);
            return true;
        });
    }

    @Test
    @DisplayName("API Test: Start Interview — no active session, creates new")
    public void testStartInterview_Success() throws Exception {
        InterviewController.StartInterviewRequest request = new InterviewController.StartInterviewRequest();
        request.setResumeId(100L);
        request.setPositionName("Java Developer");
        request.setDifficulty("Normal");

        Interview mockInterview = Interview.builder()
                .interviewId(1L).userId(TEST_UID).positionName("Java Developer").status("ONGOING").build();
        // Controller checks for an existing ONGOING interview before creating
        // a new one — return empty so it falls through to startInterview().
        when(interviewService.getUserInterviews(TEST_UID)).thenReturn(Collections.emptyList());
        when(interviewService.startInterview(eq(TEST_UID), anyLong(), anyString(), anyString(), any())).thenReturn(mockInterview);

        mockMvc.perform(post("/api/interviews/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interviewId").value(1))
                .andExpect(jsonPath("$.data.status").value("ONGOING"));
    }

    @Test
    @DisplayName("API Test: Start Interview cancels stale ongoing session instead of completing it")
    void testStartInterview_CancelsStaleSession() throws Exception {
        InterviewController.StartInterviewRequest request = new InterviewController.StartInterviewRequest();
        request.setPositionName("Frontend Engineer");
        request.setDifficulty("Normal");
        request.setMode("TEXT");

        when(interviewService.getUserInterviews(TEST_UID)).thenReturn(List.of(
                Interview.builder().interviewId(44L).userId(TEST_UID).status("ONGOING").build()
        ));
        when(interviewService.startInterview(eq(TEST_UID), isNull(), anyString(), anyString(), eq("TEXT")))
                .thenReturn(Interview.builder()
                        .interviewId(45L).userId(TEST_UID).status("ONGOING").build());

        mockMvc.perform(post("/api/interviews/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interviewId").value(45));

        verify(interviewService).cancelInterview(44L);
    }

    @Test
    @DisplayName("API Test: Send Interview Message — owner check + AI reply")
    public void testSendMessage_Success() throws Exception {
        Long interviewId = 1L;
        InterviewController.SendMessageRequest request = new InterviewController.SendMessageRequest();
        request.setContent("Tell me about yourself");

        Interview mockInterview = Interview.builder()
                .interviewId(interviewId).userId(TEST_UID)
                .positionName("Java Developer").difficulty("Normal").build();
        when(interviewService.assertOwnership(interviewId, TEST_UID)).thenReturn(mockInterview);
        when(interviewService.getInterviewMessages(interviewId)).thenReturn(Arrays.asList());
        when(aiService.chat(anyList())).thenReturn("Thank you for sharing. Can you tell me about your Java experience?");

        mockMvc.perform(post("/api/interviews/{interviewId}/message", interviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userMessage").value("Tell me about yourself"))
                .andExpect(jsonPath("$.data.aiMessage").exists());
    }

    @Test
    @DisplayName("API Test: Get Interview Messages")
    public void testGetMessages_Success() throws Exception {
        Long interviewId = 1L;
        List<InterviewMessage> messages = Arrays.asList(
                InterviewMessage.builder().msgId(1L).interviewId(interviewId).role("AI")
                        .content("Hello, let's start the interview").build(),
                InterviewMessage.builder().msgId(2L).interviewId(interviewId).role("USER")
                        .content("Sure, I'm ready").build()
        );
        when(interviewService.assertOwnership(interviewId, TEST_UID))
                .thenReturn(Interview.builder().interviewId(interviewId).build());
        when(interviewService.getInterviewMessages(interviewId)).thenReturn(messages);

        mockMvc.perform(get("/api/interviews/{interviewId}/messages", interviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("AI"))
                .andExpect(jsonPath("$.data[1].role").value("USER"));
    }

    @Test
    @DisplayName("API Test: End Interview — score now produced by report endpoint")
    public void testEndInterview_Success() throws Exception {
        Long interviewId = 1L;
        when(interviewService.assertOwnership(interviewId, TEST_UID))
                .thenReturn(Interview.builder().interviewId(interviewId).build());
        Interview completedInterview = Interview.builder()
                .interviewId(interviewId).status("COMPLETED").build();
        when(interviewService.endInterview(interviewId, null)).thenReturn(completedInterview);

        mockMvc.perform(post("/api/interviews/{interviewId}/end", interviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("API Test: Get User Interview History — caller must equal target uid")
    public void testGetUserInterviews_Success() throws Exception {
        // Path uid must match the JWT-authenticated user (TEST_UID).
        List<Interview> interviews = Arrays.asList(
                Interview.builder().interviewId(1L).userId(TEST_UID)
                        .positionName("Backend Dev").status("COMPLETED").finalScore(90).build()
        );
        when(interviewService.getUserInterviews(TEST_UID)).thenReturn(interviews);

        mockMvc.perform(get("/api/interviews/user/{userId}", TEST_UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].positionName").value("Backend Dev"))
                .andExpect(jsonPath("$.data[0].finalScore").value(90));
    }
}
