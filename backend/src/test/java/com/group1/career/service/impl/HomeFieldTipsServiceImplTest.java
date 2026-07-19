package com.group1.career.service.impl;

import com.group1.career.model.dto.HomeConsultationFeedDto;
import com.group1.career.model.entity.HomeConsultation;
import com.group1.career.repository.InterviewRepository;
import com.group1.career.service.AiService;
import com.group1.career.service.UserFactService;
import com.group1.career.service.UserProfileSnapshotService;
import com.group1.career.service.UserProfileTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HomeFieldTipsServiceImplTest {

    private AiService aiService;
    private UserProfileSnapshotService profileSnapshotService;
    private UserFactService userFactService;
    private UserProfileTagService profileTagService;
    private InterviewRepository interviewRepository;
    private StringRedisTemplate redisTemplate;
    private HomeFieldTipsServiceImpl service;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        profileSnapshotService = mock(UserProfileSnapshotService.class);
        userFactService = mock(UserFactService.class);
        profileTagService = mock(UserProfileTagService.class);
        interviewRepository = mock(InterviewRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        service = new HomeFieldTipsServiceImpl(
                aiService,
                profileSnapshotService,
                userFactService,
                profileTagService,
                interviewRepository,
                redisTemplate);
    }

    @Test
    @DisplayName("Public feed never reads profile, facts, tags, interviews, Redis, or AI")
    void publicFeedCannotEnterPersonalizationPath() {
        HomeConsultation item = HomeConsultation.builder()
                .id(1L)
                .title("公开建议")
                .bodyMd("完善一条项目经历")
                .author("CareerLoop")
                .build();

        List<HomeConsultationFeedDto> result =
                service.buildPublicConsultationFeed(123L, 3, List.of(item), List.of());

        assertEquals(1, result.size());
        assertEquals("公开建议", result.get(0).getTitle());
        verify(profileSnapshotService, never()).renderForPrompt(org.mockito.ArgumentMatchers.anyLong());
        verify(userFactService, never()).renderForPrompt(org.mockito.ArgumentMatchers.anyLong());
        verify(profileTagService, never()).renderForPrompt(org.mockito.ArgumentMatchers.anyLong());
        verify(interviewRepository, never()).findByUserIdOrderByStartedAtDesc(org.mockito.ArgumentMatchers.anyLong());
        verify(redisTemplate, never()).opsForValue();
        verify(aiService, never()).chat(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Personalized entry point rejects non-authenticated sentinel ids")
    void personalizedFeedRequiresPositiveAuthenticatedUserId() {
        assertThrows(IllegalArgumentException.class, () ->
                service.buildPersonalizedConsultationFeed(0L, 123L, 3, List.of(), List.of()));
    }
}
