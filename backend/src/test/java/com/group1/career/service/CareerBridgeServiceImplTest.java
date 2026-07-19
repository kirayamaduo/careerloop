package com.group1.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.NotificationTypes;
import com.group1.career.model.dto.AgentUserProfileDto;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionRequest;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionResponse;
import com.group1.career.model.dto.CareerBridgeDtos.LinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.StudentPassportResponse;
import com.group1.career.model.dto.UserProfileSnapshot;
import com.group1.career.model.entity.AgentTask;
import com.group1.career.model.entity.AgentUserProfile;
import com.group1.career.model.entity.User;
import com.group1.career.model.entity.UserFact;
import com.group1.career.model.entity.UserProfileTag;
import com.group1.career.repository.AgentTaskRepository;
import com.group1.career.repository.AgentUserProfileRepository;
import com.group1.career.repository.UserFactRepository;
import com.group1.career.repository.UserProfileTagRepository;
import com.group1.career.repository.UserRepository;
import com.group1.career.service.impl.CareerBridgeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerBridgeServiceImplTest {

    private static final Long STUDENT_ID = 42L;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileSnapshotService snapshotService;
    @Mock
    private AgentUserProfileRepository agentUserProfileRepository;
    @Mock
    private AgentTaskRepository taskRepository;
    @Mock
    private UserFactRepository userFactRepository;
    @Mock
    private UserProfileTagRepository userProfileTagRepository;
    @Mock
    private CheckInService checkInService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AgentEventService agentEventService;

    private ObjectMapper objectMapper;
    private CareerBridgeServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new CareerBridgeServiceImpl(
                redisTemplate,
                userRepository,
                snapshotService,
                agentUserProfileRepository,
                taskRepository,
                userFactRepository,
                userProfileTagRepository,
                checkInService,
                notificationService,
                agentEventService,
                objectMapper);
    }

    @Test
    void issuesEightDigitCodeWithTenMinuteTtl() {
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(activeUser()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(), eq(String.valueOf(STUDENT_ID)), eq(Duration.ofSeconds(600))))
                .thenReturn(true);

        LinkCodeResponse response = service.issueLinkCode(STUDENT_ID);

        assertTrue(response.getCode().matches("\\d{8}"));
        assertEquals(600, response.getExpiresInSeconds());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                key.capture(), eq(String.valueOf(STUDENT_ID)), eq(Duration.ofSeconds(600)));
        assertEquals("careerloop:bridge:link-code:" + response.getCode(), key.getValue());
    }

    @Test
    void consumesLinkCodeExactlyOnce() {
        String code = "12345678";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("careerloop:bridge:link-code:" + code))
                .thenReturn(String.valueOf(STUDENT_ID))
                .thenReturn(null);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(activeUser()));

        assertEquals(STUDENT_ID, service.consumeLinkCode(code).getStudentId());
        assertThrows(BizException.class, () -> service.consumeLinkCode(code));
        verify(valueOperations, org.mockito.Mockito.times(2))
                .getAndDelete("careerloop:bridge:link-code:" + code);
    }

    @Test
    void returnsDataMinimisedPassportWithCrossEndEvidence() throws Exception {
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 18, 16, 30);
        User user = activeUser();
        user.setNickname("Kira");
        user.setSchool("成都理工大学");
        user.setMajor("软件工程");
        user.setGraduationYear(2027);
        user.setAvatarUrl("private/avatar-object-key");
        user.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 10, 0));
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(user));

        UserProfileSnapshot snapshot = UserProfileSnapshot.builder()
                .updatedAt(LocalDateTime.of(2026, 7, 18, 11, 0))
                .preferences(UserProfileSnapshot.PreferencesBlock.builder()
                        .targetRole("Java 后端工程师")
                        .build())
                .resume(UserProfileSnapshot.ResumeBlock.builder()
                        .diagnosisScore(86)
                        .lastResumeKey("private/resume-object-key")
                        .build())
                .interview(UserProfileSnapshot.InterviewBlock.builder()
                        .lastScore(78)
                        .weakDimensions(List.of("系统设计"))
                        .build())
                .build();
        when(snapshotService.read(STUDENT_ID)).thenReturn(snapshot);

        AgentUserProfileDto.Readiness readiness = AgentUserProfileDto.Readiness.builder()
                .overallPercent(81)
                .resumeScore(86)
                .interviewScore(78)
                .hasResume(true)
                .hasInterview(true)
                .build();
        List<AgentUserProfileDto.SkillEntry> cachedSkills = List.of(
                AgentUserProfileDto.SkillEntry.builder()
                        .name("Java")
                        .category("TECHNICAL")
                        .level(82)
                        .source("RESUME")
                        .build(),
                AgentUserProfileDto.SkillEntry.builder()
                        .name("SQL")
                        .category("TECHNICAL")
                        .level(76)
                        .source("AI_EXTRACTED")
                        .build());
        AgentUserProfile profile = AgentUserProfile.builder()
                .userId(STUDENT_ID)
                .currentStage("INTERVIEW_IMPROVEMENT")
                .targetRole("Java 后端工程师")
                .readinessJson(objectMapper.writeValueAsString(readiness))
                .skillProfileJson(objectMapper.writeValueAsString(cachedSkills))
                .updatedAt(LocalDateTime.of(2026, 7, 18, 12, 0))
                .build();
        when(agentUserProfileRepository.findByUserId(STUDENT_ID)).thenReturn(Optional.of(profile));

        UserProfileTag javaTag = UserProfileTag.builder()
                .category(UserProfileTag.CATEGORY_SKILL)
                .label("Java")
                .source("USER_INPUT")
                .build();
        when(userProfileTagRepository.findByUserIdOrderByCategoryAscWeightDescUpdatedAtDesc(STUDENT_ID))
                .thenReturn(List.of(javaTag));
        when(userFactRepository.findByUserIdAndCategory(STUDENT_ID, "SKILL"))
                .thenReturn(List.of(UserFact.builder()
                        .category("SKILL")
                        .factKey("skill_python")
                        .factValue("70")
                        .source("AI_EXTRACTED")
                        .build()));

        AgentTask openTask = AgentTask.builder()
                .taskId(1L)
                .title("完成一次模拟面试")
                .taskType("INTERVIEW")
                .priority("HIGH")
                .status("TODO")
                .dueDate(LocalDate.now())
                .target("/pages/interview/start")
                .build();
        AgentTask done7d = AgentTask.builder()
                .taskId(2L)
                .title("优化项目描述")
                .taskType("RESUME")
                .priority("MEDIUM")
                .status("DONE")
                .dueDate(LocalDate.now().minusDays(1))
                .build();
        AgentTask intervention = AgentTask.builder()
                .taskId(3L)
                .title("补齐 Spring 项目证据")
                .taskType(NotificationTypes.SCHOOL_INTERVENTION)
                .priority("HIGH")
                .status("DONE")
                .dueDate(LocalDate.now().minusDays(1))
                .target("/pages/agent/index")
                .createdAt(completedAt.minusDays(1))
                .completedAt(completedAt)
                .build();
        when(taskRepository.findByUserIdAndStatusOrderByDueDateAscCreatedAtDesc(STUDENT_ID, "TODO"))
                .thenReturn(List.of(openTask));
        when(taskRepository.findByUserIdAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                eq(STUDENT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(openTask, done7d));
        when(taskRepository.findTop20ByUserIdAndTaskTypeOrderByCreatedAtDesc(
                STUDENT_ID, NotificationTypes.SCHOOL_INTERVENTION))
                .thenReturn(List.of(intervention));
        when(checkInService.getStatus(STUDENT_ID)).thenReturn(
                CheckInService.CheckInStatus.builder().streakDays(4).weeklyDays(5).build());

        StudentPassportResponse passport = service.getStudentPassport(STUDENT_ID);

        assertEquals("Kira", passport.getDisplayName());
        assertEquals("Java 后端工程师", passport.getTargetRole());
        assertEquals(81, passport.getReadiness().getOverallPercent());
        assertEquals(3, passport.getSkills().size());
        assertEquals("Java", passport.getSkills().get(0).getName());
        assertEquals(82, passport.getSkills().get(0).getLevel());
        assertEquals("USER_INPUT", passport.getSkills().get(0).getSource());
        assertEquals(4, passport.getBehavior().getStreakDays());
        assertEquals(5, passport.getBehavior().getWeeklyDays());
        assertEquals(new BigDecimal("0.50"), passport.getBehavior().getCompletionRate7d());
        assertEquals("DONE", passport.getRecentInterventions().get(0).getStatus());
        assertEquals(completedAt, passport.getRecentInterventions().get(0).getCompletedAt());
        assertEquals(1, passport.getOpenTasks().size());
        assertNotNull(passport.getGeneratedAt());

        // DTO exposes no authentication, avatar, raw resume, or object-key fields.
        String json = objectMapper.writeValueAsString(passport);
        assertFalse(json.contains("private/avatar-object-key"));
        assertFalse(json.contains("private/resume-object-key"));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("token"));
    }

    @Test
    void createsIdempotentSchoolInterventionAndAuditsSafeEvidence() {
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(activeUser()));
        when(taskRepository.findByUserIdAndTaskKey(STUDENT_ID, "school:market-gap-001"))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any(AgentTask.class))).thenAnswer(invocation -> {
            AgentTask task = invocation.getArgument(0);
            task.setTaskId(99L);
            task.setCreatedAt(LocalDateTime.now());
            return task;
        });

        InterventionRequest request = new InterventionRequest();
        request.setIdempotencyKey("market-gap-001");
        request.setTitle("补齐 Spring Boot 项目证据");
        request.setDescription("根据岗位市场差距，完成一个可展示的后端项目说明。");
        request.setPriority("HIGH");
        request.setDueDate(LocalDate.now().plusDays(2));
        request.setSkill("Spring Boot");
        request.setEvidence(Map.of(
                "marketJobCount", 128,
                "gapPercent", 37.5,
                "apiToken", "must-not-leak"));
        request.setTarget("/pages/agent/index?from=school");

        InterventionResponse response = service.createIntervention(STUDENT_ID, request);

        assertTrue(response.getCreated());
        assertEquals(99L, response.getTask().getTaskId());
        ArgumentCaptor<AgentTask> taskCaptor = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskRepository).saveAndFlush(taskCaptor.capture());
        AgentTask saved = taskCaptor.getValue();
        assertEquals("school:market-gap-001", saved.getTaskKey());
        assertEquals(NotificationTypes.SCHOOL_INTERVENTION, saved.getTaskType());
        assertEquals(NotificationTypes.SCHOOL_INTERVENTION, saved.getSource());
        assertEquals("/pages/agent/index?from=school", saved.getTarget());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(agentEventService).record(
                eq(STUDENT_ID),
                eq(NotificationTypes.SCHOOL_INTERVENTION),
                eq("SCHOOL"),
                payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals("Spring Boot", payload.get("skill"));
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) payload.get("evidence");
        assertEquals(128, evidence.get("marketJobCount"));
        assertFalse(evidence.containsKey("apiToken"));

        verify(notificationService).push(
                eq(STUDENT_ID),
                eq(NotificationTypes.SCHOOL_INTERVENTION),
                anyString(),
                eq(request.getDescription()),
                eq("/pages/agent/index?from=school"));
    }

    @Test
    void retryReturnsExistingInterventionWithoutDuplicateSideEffects() {
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(activeUser()));
        AgentTask existing = AgentTask.builder()
                .taskId(77L)
                .taskKey("school:retry-key")
                .title("已有任务")
                .taskType(NotificationTypes.SCHOOL_INTERVENTION)
                .priority("MEDIUM")
                .status("TODO")
                .dueDate(LocalDate.now())
                .target("/pages/agent/index")
                .build();
        when(taskRepository.findByUserIdAndTaskKey(STUDENT_ID, "school:retry-key"))
                .thenReturn(Optional.of(existing));

        InterventionRequest request = new InterventionRequest();
        request.setIdempotencyKey("retry-key");
        request.setTitle("不会重复创建");

        InterventionResponse response = service.createIntervention(STUDENT_ID, request);

        assertFalse(response.getCreated());
        assertEquals(77L, response.getTask().getTaskId());
        verify(taskRepository, never()).saveAndFlush(any());
        verify(agentEventService, never()).record(any(), anyString(), anyString(), any());
        verify(notificationService, never()).push(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsNonWhitelistedMiniProgramTarget() {
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(activeUser()));
        when(taskRepository.findByUserIdAndTaskKey(STUDENT_ID, "school:bad-target"))
                .thenReturn(Optional.empty());
        InterventionRequest request = new InterventionRequest();
        request.setIdempotencyKey("bad-target");
        request.setTitle("恶意跳转");
        request.setTarget("https://evil.example/phishing");

        assertThrows(BizException.class, () -> service.createIntervention(STUDENT_ID, request));
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletedStudentReturnsAccountDeletedSoWebsiteCanUnlink() {
        User deleted = activeUser();
        deleted.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(deleted));

        BizException error = assertThrows(
                BizException.class, () -> service.getStudentPassport(STUDENT_ID));

        assertEquals(ErrorCode.ACCOUNT_DELETED.getCode(), error.getCode());
        verify(snapshotService, never()).read(any());
    }

    @Test
    void bannedStudentReturnsForbiddenWithoutExposingPassport() {
        User banned = activeUser();
        banned.setStatus(2);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(banned));

        BizException error = assertThrows(
                BizException.class, () -> service.getStudentPassport(STUDENT_ID));

        assertEquals(403, error.getCode());
        assertEquals(ErrorCode.ACCOUNT_BANNED.getMessage(), error.getMessage());
        verify(snapshotService, never()).read(any());
    }

    private User activeUser() {
        return User.builder().userId(STUDENT_ID).status(1).build();
    }
}
