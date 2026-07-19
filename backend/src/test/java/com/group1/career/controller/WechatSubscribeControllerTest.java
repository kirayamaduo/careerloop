package com.group1.career.controller;

import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.repository.WxSubscribeQuotaRepository;
import com.group1.career.service.WechatSubscribeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WechatSubscribeController.class)
class WechatSubscribeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WechatSubscribeService subscribeService;

    @MockitoBean
    private WxSubscribeQuotaRepository quotaRepository;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void authenticate() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", 7L);
            return true;
        });
    }

    @Test
    void templatesEndpointReturnsOnlyServiceConfiguredTemplates() throws Exception {
        when(subscribeService.getConfiguredTemplates()).thenReturn(Map.of(
                "weekly", "weekly-id",
                "interview", "interview-id"
        ));

        mockMvc.perform(get("/api/wx-subscribe/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.weekly").value("weekly-id"))
                .andExpect(jsonPath("$.data.interview").value("interview-id"))
                .andExpect(jsonPath("$.data.assessment").doesNotExist());
    }
}
