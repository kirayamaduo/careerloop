package com.group1.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.model.entity.CareerNode;
import com.group1.career.model.entity.CareerPath;
import com.group1.career.model.entity.UserCareerProgress;
import com.group1.career.service.AdminAuthService;
import com.group1.career.service.CareerPlanService;
import com.group1.career.service.CareerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CareerController.class)
public class CareerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CareerService careerService;

    @MockitoBean
    private CareerPlanService careerPlanService;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void bypassAuth() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("API Test: Get All Career Paths")
    public void testGetAllPaths_Success() throws Exception {
        CareerPath path1 = CareerPath.builder()
                .pathId(1).code("java-backend").name("Java Backend Engineer")
                .description("Become an excellent Java backend developer").build();
        when(careerService.getAllPaths()).thenReturn(Arrays.asList(path1));

        mockMvc.perform(get("/api/careers/paths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].pathId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Java Backend Engineer"));
    }

    @Test
    @DisplayName("API Test: Get Career Path by ID")
    public void testGetPath_Success() throws Exception {
        Integer pathId = 1;
        CareerPath mockPath = CareerPath.builder().pathId(pathId).name("Frontend Engineer").build();
        when(careerService.getPathById(pathId)).thenReturn(mockPath);

        mockMvc.perform(get("/api/careers/paths/{pathId}", pathId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pathId").value(pathId))
                .andExpect(jsonPath("$.data.name").value("Frontend Engineer"));
    }

    @Test
    @DisplayName("API Test: Get Path Nodes")
    public void testGetPathNodes_Success() throws Exception {
        Integer pathId = 1;
        List<CareerNode> nodes = Arrays.asList(
                CareerNode.builder().nodeId(1L).name("Java Basics").level(1).build(),
                CareerNode.builder().nodeId(2L).name("Spring Boot").level(2).build()
        );
        when(careerService.getPathNodes(pathId)).thenReturn(nodes);

        mockMvc.perform(get("/api/careers/paths/{pathId}/nodes", pathId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Java Basics"))
                .andExpect(jsonPath("$.data[1].level").value(2));
    }

    @Test
    @DisplayName("API Test: Get User Progress")
    public void testGetUserProgress_Success() throws Exception {
        Long authenticatedUserId = 7L;
        List<UserCareerProgress> progress = Arrays.asList(
                UserCareerProgress.builder().id(1L).userId(authenticatedUserId).nodeId(1L).status("COMPLETED").build()
        );
        when(careerService.getUserProgress(authenticatedUserId)).thenReturn(progress);

        // The legacy path id is deliberately different. The controller must
        // use only the authenticated request attribute populated from the JWT.
        mockMvc.perform(get("/api/careers/progress/{userId}", 999L)
                        .requestAttr("userId", authenticatedUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(authenticatedUserId))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
        verify(careerService).getUserProgress(authenticatedUserId);
        verify(authInterceptor).preHandle(any(), any(), any());
    }

    @Test
    @DisplayName("API Test: Unlock Node")
    public void testUnlockNode_Success() throws Exception {
        CareerController.UnlockNodeRequest request = new CareerController.UnlockNodeRequest();
        request.setUserId(1L);
        request.setNodeId(10L);
        doNothing().when(careerService).unlockNode(anyLong(), anyLong());

        mockMvc.perform(post("/api/careers/progress/unlock")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Node unlocked successfully"));
        verify(careerService).unlockNode(7L, 10L);
    }

    @Test
    @DisplayName("API Test: Complete Node")
    public void testCompleteNode_Success() throws Exception {
        CareerController.CompleteNodeRequest request = new CareerController.CompleteNodeRequest();
        request.setUserId(1L);
        request.setNodeId(10L);
        doNothing().when(careerService).completeNode(anyLong(), anyLong());

        mockMvc.perform(post("/api/careers/progress/complete")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Node completed successfully"));
        verify(careerService).completeNode(7L, 10L);
    }

    @Test
    @DisplayName("API Test: Timeline ignores caller-supplied userId")
    public void testTimeline_UsesAuthenticatedUser() throws Exception {
        when(careerService.getPathNodes(3)).thenReturn(List.of(
                CareerNode.builder().nodeId(10L).name("Java Basics").level(1).build()
        ));
        when(careerService.getUserProgress(7L)).thenReturn(List.of(
                UserCareerProgress.builder()
                        .userId(7L)
                        .nodeId(10L)
                        .status("COMPLETED")
                        .build()
        ));

        mockMvc.perform(get("/api/careers/timeline")
                        .param("pathId", "3")
                        .param("userId", "999")
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.l1Nodes[0].status").value("COMPLETED"));

        verify(careerService).getUserProgress(7L);
    }

    @Test
    @DisplayName("API Test: Initialize Paths")
    public void testInitializePaths_Success() throws Exception {
        doNothing().when(careerService).initializeDefaultPaths();
        doNothing().when(adminAuthService).requireAdmin(7L);

        mockMvc.perform(post("/api/careers/initialize")
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Career paths initialized successfully"));
        verify(adminAuthService).requireAdmin(7L);
    }
}
