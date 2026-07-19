package com.group1.career.controller;

import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.model.entity.Interview;
import com.group1.career.model.entity.InterviewMessage;
import com.group1.career.service.AiService;
import com.group1.career.service.BodyLanguageService;
import com.group1.career.service.InterviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewReportController.class)
class InterviewReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewService interviewService;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private BodyLanguageService bodyLanguageService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void authenticate() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", 1L);
            return true;
        });
    }

    @Test
    void cachedReportReturnsWithoutAnotherAiCall() throws Exception {
        String cached = """
                {"interviewId":5,"positionName":"Java Engineer","difficulty":"Normal","mode":"TEXT",
                "overallScore":82,"totalQuestions":1,
                "radarChart":{"expression":80,"logic":84,"technical":83,"pressureResistance":81,"communication":82},
                "strengths":[],"improvements":[],"textSummary":"表现稳定"}
                """;
        when(interviewService.lockForReport(5L, 1L)).thenReturn(Interview.builder()
                .interviewId(5L)
                .userId(1L)
                .status("COMPLETED")
                .mode("TEXT")
                .reportJson(cached)
                .build());

        mockMvc.perform(get("/api/interviews/report/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").value(82))
                .andExpect(jsonPath("$.data.textSummary").value("表现稳定"));

        verify(aiService, never()).chat(anyString());
        verify(interviewService, never()).saveReport(anyLong(), anyString(), anyInt());
    }

    @Test
    void firstReportIsGeneratedAndPersisted() throws Exception {
        Interview interview = Interview.builder()
                .interviewId(8L)
                .userId(1L)
                .positionName("Java Engineer")
                .difficulty("Normal")
                .mode("TEXT")
                .status("COMPLETED")
                .build();
        when(interviewService.lockForReport(8L, 1L)).thenReturn(interview);
        when(interviewService.getInterviewMessages(8L)).thenReturn(List.of(
                InterviewMessage.builder().role("AI").content("请介绍项目").build(),
                InterviewMessage.builder().role("USER").content("我负责后端接口设计").build()
        ));
        when(aiService.chat(anyString())).thenReturn("""
                {"overallScore":78,
                "radarChart":{"expression":76,"logic":80,"technical":82,"pressureResistance":74,"communication":78},
                "strengths":[{"title":"技术清晰","detail":"说明了接口设计职责"}],
                "improvements":[],"summary":"回答有证据。"}
                """);

        mockMvc.perform(get("/api/interviews/report/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").value(78))
                .andExpect(jsonPath("$.data.totalQuestions").value(1));

        verify(aiService).chat(anyString());
        verify(interviewService).saveReport(eq(8L), anyString(), eq(78));
    }

    @Test
    void ongoingInterviewCannotGenerateAReport() throws Exception {
        when(interviewService.lockForReport(9L, 1L)).thenReturn(Interview.builder()
                .interviewId(9L)
                .userId(1L)
                .status("ONGOING")
                .build());

        mockMvc.perform(get("/api/interviews/report/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(aiService, never()).chat(anyString());
    }

    @Test
    void incompleteAiEvaluationIsRejectedAndNeverCached() throws Exception {
        Interview interview = Interview.builder()
                .interviewId(10L)
                .userId(1L)
                .positionName("Java Engineer")
                .difficulty("Normal")
                .mode("TEXT")
                .status("COMPLETED")
                .build();
        when(interviewService.lockForReport(10L, 1L)).thenReturn(interview);
        when(interviewService.getInterviewMessages(10L)).thenReturn(List.of(
                InterviewMessage.builder().role("AI").content("请介绍项目").build(),
                InterviewMessage.builder().role("USER").content("我负责后端接口设计").build()
        ));
        // Syntactically valid JSON used to become a polished-looking 70-point
        // report because every missing radar dimension had a default value.
        when(aiService.chat(anyString())).thenReturn("{\"overallScore\":88}");

        mockMvc.perform(get("/api/interviews/report/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("AI evaluation was incomplete. Please retry."));

        verify(interviewService, never()).saveReport(anyLong(), anyString(), anyInt());
    }

    @Test
    void incompleteCachedReportIsRegeneratedInsteadOfReturned() throws Exception {
        Interview interview = Interview.builder()
                .interviewId(11L)
                .userId(1L)
                .positionName("Java Engineer")
                .difficulty("Normal")
                .mode("TEXT")
                .status("COMPLETED")
                .reportJson("{\"overallScore\":70,\"radarChart\":{}}")
                .build();
        when(interviewService.lockForReport(11L, 1L)).thenReturn(interview);
        when(interviewService.getInterviewMessages(11L)).thenReturn(List.of(
                InterviewMessage.builder().role("AI").content("请介绍项目").build(),
                InterviewMessage.builder().role("USER").content("我负责后端接口设计").build()
        ));
        when(aiService.chat(anyString())).thenReturn("""
                {"overallScore":78,
                "radarChart":{"expression":76,"logic":80,"technical":82,"pressureResistance":74,"communication":78},
                "strengths":[{"title":"技术清晰","detail":"说明了接口设计职责"}],
                "improvements":[{"title":"补充结果","detail":"可以进一步说明量化结果"}],
                "summary":"回答有证据。"}
                """);

        mockMvc.perform(get("/api/interviews/report/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").value(78));

        verify(aiService).chat(anyString());
        verify(interviewService).saveReport(eq(11L), anyString(), eq(78));
    }
}
