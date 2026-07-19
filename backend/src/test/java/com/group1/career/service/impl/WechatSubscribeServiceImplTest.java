package com.group1.career.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.group1.career.model.entity.UserAuth;
import com.group1.career.model.entity.WxSubscribeQuota;
import com.group1.career.repository.UserAuthRepository;
import com.group1.career.repository.WxSubscribeQuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class WechatSubscribeServiceImplTest {

    private static final String WEEKLY_TEMPLATE = "weekly-template";

    private WxSubscribeQuotaRepository quotaRepository;
    private UserAuthRepository userAuthRepository;
    private WechatSubscribeServiceImpl service;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(WxSubscribeQuotaRepository.class);
        userAuthRepository = mock(UserAuthRepository.class);
        service = spy(new WechatSubscribeServiceImpl(
                mock(StringRedisTemplate.class),
                quotaRepository,
                userAuthRepository,
                new ObjectMapper()));
        ReflectionTestUtils.setField(service, "tplWeeklyReport", WEEKLY_TEMPLATE);
        ReflectionTestUtils.setField(service, "tplInterviewReport", "");
        ReflectionTestUtils.setField(service, "tplAssessment", "");
        ReflectionTestUtils.setField(service, "tplResumeDiagnosis", "");
        ReflectionTestUtils.setField(service, "tplAiProactive", "");
    }

    @Test
    @DisplayName("Grant endpoint ignores template IDs not configured by the server")
    void recordGrantRejectsUnconfiguredTemplate() {
        service.recordGrant(7L, Map.of("attacker-controlled-template", "accept"));

        verify(quotaRepository, never()).findByUserIdAndTemplateId(any(), anyString());
        verify(quotaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Accepted configured template increments its quota")
    void recordGrantAcceptsConfiguredTemplate() {
        WxSubscribeQuota quota = WxSubscribeQuota.builder()
                .userId(7L)
                .templateId(WEEKLY_TEMPLATE)
                .remaining(2)
                .build();
        when(quotaRepository.findByUserIdAndTemplateId(7L, WEEKLY_TEMPLATE))
                .thenReturn(Optional.of(quota));

        service.recordGrant(7L, Map.of(WEEKLY_TEMPLATE, "accept"));

        verify(quotaRepository).save(quota);
        assertEquals(3, quota.getRemaining());
    }

    @Test
    @DisplayName("Template discovery exposes only configured templates with real send flows")
    void configuredTemplatesOmitBlankAndUnsupportedEntries() {
        ReflectionTestUtils.setField(service, "tplWeeklyReport", " weekly-template ");
        ReflectionTestUtils.setField(service, "tplAssessment", "assessment-template");
        ReflectionTestUtils.setField(service, "tplInterviewReport", "");
        ReflectionTestUtils.setField(service, "tplResumeDiagnosis", "resume-template");
        ReflectionTestUtils.setField(service, "tplAiProactive", "ai-template");

        assertEquals(
                Map.of(
                        "weekly", "weekly-template",
                        "assessment", "assessment-template"
                ),
                service.getConfiguredTemplates()
        );
    }

    @Test
    @DisplayName("Missing WeChat identity never consumes a one-time grant")
    void missingOpenidDoesNotConsumeQuota() {
        when(userAuthRepository.findByUserId(7L)).thenReturn(List.of());

        assertFalse(service.send(7L, WEEKLY_TEMPLATE, "/pages/home/index", Map.of()));
        verify(quotaRepository, never()).decrementRemaining(any(), anyString());
    }

    @Test
    @DisplayName("Failed WeChat dispatch restores the consumed quota")
    void failedDispatchRestoresQuota() throws Exception {
        when(userAuthRepository.findByUserId(7L)).thenReturn(List.of(wechatAuth()));
        doReturn("access-token").when(service).getAccessToken();
        when(quotaRepository.decrementRemaining(7L, WEEKLY_TEMPLATE)).thenReturn(1);
        doReturn(false).when(service).dispatch(anyString(), any(ObjectNode.class));
        when(quotaRepository.incrementRemaining(7L, WEEKLY_TEMPLATE)).thenReturn(1);

        assertFalse(service.send(7L, WEEKLY_TEMPLATE, "/pages/home/index", Map.of()));
        verify(quotaRepository).incrementRemaining(7L, WEEKLY_TEMPLATE);
    }

    @Test
    @DisplayName("Successful WeChat dispatch consumes exactly one quota")
    void successfulDispatchConsumesQuotaWithoutRefund() throws Exception {
        when(userAuthRepository.findByUserId(7L)).thenReturn(List.of(wechatAuth()));
        doReturn("access-token").when(service).getAccessToken();
        when(quotaRepository.decrementRemaining(7L, WEEKLY_TEMPLATE)).thenReturn(1);
        doReturn(true).when(service).dispatch(anyString(), any(ObjectNode.class));

        assertTrue(service.send(7L, WEEKLY_TEMPLATE, "/pages/home/index", Map.of()));
        verify(quotaRepository, never()).incrementRemaining(any(), anyString());
    }

    private UserAuth wechatAuth() {
        return UserAuth.builder()
                .userId(7L)
                .identityType("WECHAT")
                .identifier("openid-7")
                .build();
    }
}
