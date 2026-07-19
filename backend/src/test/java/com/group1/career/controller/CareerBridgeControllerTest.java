package com.group1.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.interceptor.CareerBridgeInterceptor;
import com.group1.career.exception.BizException;
import com.group1.career.model.dto.CareerBridgeDtos.ConsumeLinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionResponse;
import com.group1.career.model.dto.CareerBridgeDtos.LinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.StudentPassportResponse;
import com.group1.career.model.dto.CareerBridgeDtos.TaskSummary;
import com.group1.career.service.CareerBridgeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {IntegrationLinkController.class, CareerPlatformBridgeController.class},
        properties = {
                "career.bridge.key=test-bridge-key",
                "career.bridge.public-url=https://career.example.com/passport"
        })
@Import(CareerBridgeInterceptor.class)
class CareerBridgeControllerTest {

    private static final Long STUDENT_ID = 42L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CareerBridgeService careerBridgeService;
    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void authenticatePublicEndpoint() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", STUDENT_ID);
            return true;
        });
    }

    @Test
    void publicLinkCodeEndpointUsesJwtIdentityAndResultEnvelope() throws Exception {
        when(careerBridgeService.issueLinkCode(STUDENT_ID)).thenReturn(
                LinkCodeResponse.builder().code("12345678").expiresInSeconds(600).build());

        mockMvc.perform(post("/api/integration/link-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").value("12345678"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600));
    }

    @Test
    void linkCodeFailsClosedWithoutTrustedHttpsPassportEntry() {
        IntegrationLinkController controller = new IntegrationLinkController(careerBridgeService, "");

        BizException error = assertThrows(BizException.class, controller::issueLinkCode);

        assertTrue(error.getMessage().contains("安全入口暂未配置"));
        verifyNoInteractions(careerBridgeService);
    }

    @Test
    void passportEntryRejectsHttpAndIpLiteralsButAllowsTrustedDomainPorts() {
        assertFalse(IntegrationLinkController.isTrustedPassportUrl(
                "http://43.138.240.228:9178/passport"));
        assertFalse(IntegrationLinkController.isTrustedPassportUrl(
                "https://43.138.240.228:9443/passport"));
        assertTrue(IntegrationLinkController.isTrustedPassportUrl(
                "https://api.careerloop.top:9443/passport"));
    }

    @Test
    void internalEndpointRejectsMissingAndWrongBridgeKey() throws Exception {
        mockMvc.perform(post("/internal/career-platform/v1/link-code/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"12345678\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/internal/career-platform/v1/link-code/consume")
                        .header(CareerBridgeInterceptor.HEADER_NAME, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"12345678\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void internalConsumeUsesDedicatedKeyAndCamelCaseContract() throws Exception {
        when(careerBridgeService.consumeLinkCode("12345678")).thenReturn(
                ConsumeLinkCodeResponse.builder()
                        .studentId(STUDENT_ID)
                        .source("CAREERLOOP")
                        .build());

        mockMvc.perform(post("/internal/career-platform/v1/link-code/consume")
                        .header(CareerBridgeInterceptor.HEADER_NAME, "test-bridge-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studentId").value(STUDENT_ID))
                .andExpect(jsonPath("$.data.source").value("CAREERLOOP"));
    }

    @Test
    void internalConsumeValidatesEightDigitCode() throws Exception {
        mockMvc.perform(post("/internal/career-platform/v1/link-code/consume")
                        .header(CareerBridgeInterceptor.HEADER_NAME, "test-bridge-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void passportAndInterventionContractsExposeCrossEndLoop() throws Exception {
        StudentPassportResponse passport = StudentPassportResponse.builder()
                .source("CAREERLOOP")
                .studentId(STUDENT_ID)
                .displayName("Kira")
                .skills(List.of())
                .recentInterventions(List.of())
                .openTasks(List.of())
                .build();
        when(careerBridgeService.getStudentPassport(STUDENT_ID)).thenReturn(passport);
        TaskSummary task = TaskSummary.builder()
                .taskId(9L)
                .title("补齐 Spring 技能证据")
                .status("TODO")
                .link("/pages/agent/index")
                .build();
        when(careerBridgeService.createIntervention(eq(STUDENT_ID), any())).thenReturn(
                InterventionResponse.builder().created(true).task(task).build());

        mockMvc.perform(get("/internal/career-platform/v1/students/{studentId}/passport", STUDENT_ID)
                        .header(CareerBridgeInterceptor.HEADER_NAME, "test-bridge-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(STUDENT_ID))
                .andExpect(jsonPath("$.data.skills").isArray())
                .andExpect(jsonPath("$.data.recentInterventions").isArray());

        Map<String, Object> request = Map.of(
                "idempotencyKey", "market-gap-9",
                "title", "补齐 Spring 技能证据",
                "skill", "Spring",
                "evidence", Map.of("marketJobCount", 128),
                "target", "/pages/agent/index");
        mockMvc.perform(post(
                        "/internal/career-platform/v1/students/{studentId}/interventions",
                        STUDENT_ID)
                        .header(CareerBridgeInterceptor.HEADER_NAME, "test-bridge-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.task.status").value("TODO"))
                .andExpect(jsonPath("$.data.task.link").value("/pages/agent/index"));
    }
}
