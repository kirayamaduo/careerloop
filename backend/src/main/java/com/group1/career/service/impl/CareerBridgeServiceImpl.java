package com.group1.career.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.exception.BizException;
import com.group1.career.model.NotificationTypes;
import com.group1.career.model.dto.AgentUserProfileDto;
import com.group1.career.model.dto.CareerBridgeDtos.Behavior;
import com.group1.career.model.dto.CareerBridgeDtos.ConsumeLinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.Education;
import com.group1.career.model.dto.CareerBridgeDtos.Evidence;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionRequest;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionResponse;
import com.group1.career.model.dto.CareerBridgeDtos.LinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.Readiness;
import com.group1.career.model.dto.CareerBridgeDtos.SkillSummary;
import com.group1.career.model.dto.CareerBridgeDtos.StudentPassportResponse;
import com.group1.career.model.dto.CareerBridgeDtos.TaskSummary;
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
import com.group1.career.service.AgentEventService;
import com.group1.career.service.CareerBridgeService;
import com.group1.career.service.CheckInService;
import com.group1.career.service.NotificationService;
import com.group1.career.service.UserProfileSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerBridgeServiceImpl implements CareerBridgeService {

    static final int LINK_CODE_TTL_SECONDS = 600;
    static final String LINK_CODE_KEY_PREFIX = "careerloop:bridge:link-code:";
    static final String BRIDGE_SOURCE = "CAREERLOOP";
    static final String DEFAULT_AGENT_LINK = "/pages/agent/index";
    private static final int MAX_CODE_ATTEMPTS = 12;

    private static final Set<String> ALLOWED_TARGET_PATHS = Set.of(
            DEFAULT_AGENT_LINK,
            "/pages/agent/profile",
            "/pages/resume/index",
            "/pages/resume-ai/index",
            "/pages/interview/start",
            "/pages/interview/history",
            "/pages/assessment/index",
            "/pages/map/index",
            "/pages/checkin/index"
    );
    private static final Pattern SAFE_QUERY = Pattern.compile("[A-Za-z0-9_=&%.,:+-]{0,200}");
    private static final Pattern SAFE_EVIDENCE_KEY = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern SENSITIVE_EVIDENCE_KEY =
            Pattern.compile(".*(password|passwd|secret|token|credential|authorization|api.?key).*",
                    Pattern.CASE_INSENSITIVE);

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final UserProfileSnapshotService snapshotService;
    private final AgentUserProfileRepository agentUserProfileRepository;
    private final AgentTaskRepository taskRepository;
    private final UserFactRepository userFactRepository;
    private final UserProfileTagRepository userProfileTagRepository;
    private final CheckInService checkInService;
    private final NotificationService notificationService;
    private final AgentEventService agentEventService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public LinkCodeResponse issueLinkCode(Long userId) {
        requireActiveUser(userId);
        try {
            for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
                String code = String.format(Locale.ROOT, "%08d", secureRandom.nextInt(100_000_000));
                Boolean stored = redisTemplate.opsForValue().setIfAbsent(
                        redisKey(code), String.valueOf(userId), Duration.ofSeconds(LINK_CODE_TTL_SECONDS));
                if (Boolean.TRUE.equals(stored)) {
                    return LinkCodeResponse.builder()
                            .code(code)
                            .expiresInSeconds(LINK_CODE_TTL_SECONDS)
                            .build();
                }
            }
        } catch (DataAccessException exception) {
            log.warn("[career-bridge] Redis unavailable while issuing link code: {}", exception.toString());
            throw new BizException(503, "Link service unavailable");
        }
        throw new BizException(503, "Unable to allocate link code");
    }

    @Override
    public ConsumeLinkCodeResponse consumeLinkCode(String code) {
        if (code == null || !code.matches("\\d{8}")) {
            throw new BizException("Invalid or expired link code");
        }

        final String userIdValue;
        try {
            // Redis GETDEL is atomic: the first successful consumer wins and
            // every subsequent attempt observes an absent key.
            userIdValue = redisTemplate.opsForValue().getAndDelete(redisKey(code));
        } catch (DataAccessException exception) {
            log.warn("[career-bridge] Redis unavailable while consuming link code: {}", exception.toString());
            throw new BizException(503, "Link service unavailable");
        }
        if (userIdValue == null || userIdValue.isBlank()) {
            throw new BizException("Invalid or expired link code");
        }

        try {
            Long userId = Long.valueOf(userIdValue);
            requireActiveUser(userId);
            return ConsumeLinkCodeResponse.builder()
                    .studentId(userId)
                    .source(BRIDGE_SOURCE)
                    .build();
        } catch (NumberFormatException exception) {
            log.warn("[career-bridge] malformed user id stored for a link code");
            throw new BizException("Invalid or expired link code");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StudentPassportResponse getStudentPassport(Long studentId) {
        User user = requireActiveUser(studentId);
        UserProfileSnapshot snapshot = snapshotService.read(studentId);
        AgentUserProfile cachedProfile = agentUserProfileRepository.findByUserId(studentId).orElse(null);
        AgentUserProfileDto.Readiness cachedReadiness = parseReadiness(cachedProfile);

        List<AgentTask> openTasks = taskRepository
                .findByUserIdAndStatusOrderByDueDateAscCreatedAtDesc(studentId, "TODO");
        List<TaskSummary> safeTasks = openTasks.stream()
                .limit(20)
                .map(this::toTaskSummary)
                .toList();
        List<AgentTask> tasks7d = taskRepository
                .findByUserIdAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                        studentId, LocalDate.now().minusDays(6), LocalDate.now());
        List<TaskSummary> recentInterventions = taskRepository
                .findTop20ByUserIdAndTaskTypeOrderByCreatedAtDesc(
                        studentId, NotificationTypes.SCHOOL_INTERVENTION)
                .stream()
                .map(this::toTaskSummary)
                .toList();
        List<SkillSummary> skills = buildSkills(
                cachedProfile,
                userProfileTagRepository.findByUserIdOrderByCategoryAscWeightDescUpdatedAtDesc(studentId),
                userFactRepository.findByUserIdAndCategory(studentId, "SKILL"));
        Behavior behavior = buildBehavior(checkInService.getStatus(studentId), tasks7d);

        UserProfileSnapshot.ResumeBlock resume = snapshot.getResume();
        UserProfileSnapshot.InterviewBlock interview = snapshot.getInterview();
        UserProfileSnapshot.OnboardingBlock onboarding = snapshot.getOnboarding();

        String targetRole = firstText(
                cachedProfile != null ? cachedProfile.getTargetRole() : null,
                snapshot.getPreferences() != null ? snapshot.getPreferences().getTargetRole() : null,
                resume != null ? resume.getTargetJob() : null,
                interview != null ? interview.getPositionName() : null
        );
        String currentStage = firstText(
                cachedProfile != null ? cachedProfile.getCurrentStage() : null,
                onboarding != null ? onboarding.getStage() : null,
                onboarding != null ? onboarding.getIdentityType() : null
        );

        return StudentPassportResponse.builder()
                .source(BRIDGE_SOURCE)
                .studentId(user.getUserId())
                .displayName(user.getNickname())
                .education(Education.builder()
                        .school(user.getSchool())
                        .major(user.getMajor())
                        .graduationYear(user.getGraduationYear())
                        .build())
                .currentStage(currentStage)
                .targetRole(targetRole)
                .readiness(toReadiness(cachedReadiness, snapshot))
                .skills(skills)
                .behavior(behavior)
                .evidence(Evidence.builder()
                        .resumeDiagnosisScore(resume != null ? resume.getDiagnosisScore() : null)
                        .lastInterviewScore(interview != null ? interview.getLastScore() : null)
                        .interviewWeakDimensions(interview != null
                                ? nullSafeList(interview.getWeakDimensions()) : List.of())
                        .openTaskCount(openTasks.size())
                        .build())
                .openTasks(safeTasks)
                .recentInterventions(recentInterventions)
                .sourceUpdatedAt(latestUpdate(user, snapshot, cachedProfile))
                .generatedAt(LocalDateTime.now().toString())
                .build();
    }

    @Override
    @Transactional
    public synchronized InterventionResponse createIntervention(
            Long studentId, InterventionRequest request) {
        requireActiveUser(studentId);
        if (request == null) {
            throw new BizException("Intervention request is required");
        }
        if (!hasText(request.getIdempotencyKey())
                || !request.getIdempotencyKey().matches("[A-Za-z0-9._-]{1,100}")) {
            throw new BizException("Invalid idempotencyKey");
        }
        if (!hasText(request.getTitle()) || request.getTitle().trim().length() > 160) {
            throw new BizException("Invalid intervention title");
        }
        if (hasText(request.getPriority())
                && !Set.of("LOW", "MEDIUM", "HIGH").contains(request.getPriority())) {
            throw new BizException("Invalid intervention priority");
        }

        String taskKey = "school:" + request.getIdempotencyKey().trim();
        Optional<AgentTask> existing = taskRepository.findByUserIdAndTaskKey(studentId, taskKey);
        if (existing.isPresent()) {
            return InterventionResponse.builder()
                    .created(false)
                    .task(toTaskSummary(existing.get()))
                    .build();
        }

        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : LocalDate.now();
        if (dueDate.isBefore(LocalDate.now())) {
            throw new BizException("dueDate must be today or later");
        }
        String target = validateTarget(request.getTarget());
        String priority = hasText(request.getPriority()) ? request.getPriority() : "MEDIUM";

        AgentTask task = taskRepository.saveAndFlush(AgentTask.builder()
                .userId(studentId)
                .taskKey(taskKey)
                .title(request.getTitle().trim())
                .description(trimToNull(request.getDescription()))
                .taskType(NotificationTypes.SCHOOL_INTERVENTION)
                .priority(priority)
                .status("TODO")
                .target(target)
                .source(NotificationTypes.SCHOOL_INTERVENTION)
                .dueDate(dueDate)
                .build());

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("taskId", task.getTaskId());
        auditPayload.put("taskKey", task.getTaskKey());
        auditPayload.put("idempotencyKey", request.getIdempotencyKey());
        auditPayload.put("title", task.getTitle());
        auditPayload.put("dueDate", task.getDueDate().toString());
        auditPayload.put("target", target);
        if (hasText(request.getSkill())) {
            auditPayload.put("skill", request.getSkill().trim());
        }
        Map<String, Object> safeEvidence = sanitizeEvidence(request.getEvidence());
        if (!safeEvidence.isEmpty()) {
            auditPayload.put("evidence", safeEvidence);
        }

        agentEventService.record(
                studentId, NotificationTypes.SCHOOL_INTERVENTION, "SCHOOL", auditPayload);
        notificationService.push(
                studentId,
                NotificationTypes.SCHOOL_INTERVENTION,
                "高校就业服务为你安排了新行动",
                notificationContent(request),
                target);

        return InterventionResponse.builder()
                .created(true)
                .task(toTaskSummary(task))
                .build();
    }

    private User requireActiveUser(Long userId) {
        if (userId == null) {
            throw new BizException("Student not found");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("Student not found"));
        if (user.getDeletedAt() != null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException("Student is not active");
        }
        return user;
    }

    private AgentUserProfileDto.Readiness parseReadiness(AgentUserProfile profile) {
        if (profile == null || !hasText(profile.getReadinessJson())) return null;
        try {
            return objectMapper.readValue(profile.getReadinessJson(), AgentUserProfileDto.Readiness.class);
        } catch (Exception exception) {
            log.warn("[career-bridge] invalid cached readiness for student {}", profile.getUserId());
            return null;
        }
    }

    private Readiness toReadiness(
            AgentUserProfileDto.Readiness cached, UserProfileSnapshot snapshot) {
        UserProfileSnapshot.ResumeBlock resume = snapshot.getResume();
        UserProfileSnapshot.InterviewBlock interview = snapshot.getInterview();
        boolean hasAssessment = snapshot.getAssessment() != null;
        boolean hasResume = resume != null;
        boolean hasInterview = interview != null;

        return Readiness.builder()
                .overallPercent(cached != null ? cached.getOverallPercent() : null)
                .resumeScore(cached != null && cached.getResumeScore() != null
                        ? cached.getResumeScore() : hasResume ? resume.getDiagnosisScore() : null)
                .interviewScore(cached != null && cached.getInterviewScore() != null
                        ? cached.getInterviewScore() : hasInterview ? interview.getLastScore() : null)
                .hasAssessment(cached != null && cached.getHasAssessment() != null
                        ? cached.getHasAssessment() : hasAssessment)
                .hasResume(cached != null && cached.getHasResume() != null
                        ? cached.getHasResume() : hasResume)
                .hasInterview(cached != null && cached.getHasInterview() != null
                        ? cached.getHasInterview() : hasInterview)
                .hasPlan(cached != null ? cached.getHasPlan() : null)
                .directionClarityPercent(cached != null ? cached.getDirectionClarityPercent() : null)
                .resumeReadinessPercent(cached != null ? cached.getResumeReadinessPercent() : null)
                .interviewReadinessPercent(cached != null ? cached.getInterviewReadinessPercent() : null)
                .actionContinuityPercent(cached != null ? cached.getActionContinuityPercent() : null)
                .build();
    }

    private TaskSummary toTaskSummary(AgentTask task) {
        return TaskSummary.builder()
                .taskId(task.getTaskId())
                .title(task.getTitle())
                .taskType(task.getTaskType())
                .priority(task.getPriority())
                .status(task.getStatus())
                .dueDate(task.getDueDate())
                .link(task.getTarget())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }

    private List<SkillSummary> buildSkills(
            AgentUserProfile cachedProfile,
            List<UserProfileTag> tags,
            List<UserFact> facts) {
        Map<String, SkillSummary> byName = new LinkedHashMap<>();

        // User-editable portrait tags are the freshest explicit source. Their
        // weight is ranking confidence, not proficiency, so it is deliberately
        // not repurposed as a made-up skill level.
        nullSafeList(tags).stream()
                .filter(tag -> UserProfileTag.CATEGORY_SKILL.equals(tag.getCategory()))
                .map(tag -> SkillSummary.builder()
                        .name(cleanSkillName(tag.getLabel()))
                        .category(tag.getCategory())
                        .level(null)
                        .source(trimToNull(tag.getSource()))
                        .build())
                .forEach(skill -> mergeSkill(byName, skill));

        for (AgentUserProfileDto.SkillEntry skill : parseCachedSkills(cachedProfile)) {
            mergeSkill(byName, SkillSummary.builder()
                    .name(cleanSkillName(skill.getName()))
                    .category(trimToNull(skill.getCategory()))
                    .level(validSkillLevel(skill.getLevel()))
                    .source(trimToNull(skill.getSource()))
                    .build());
        }

        nullSafeList(facts).stream()
                .map(fact -> SkillSummary.builder()
                        .name(skillNameFromFact(fact))
                        .category(fact.getCategory())
                        .level(parseSkillLevel(fact.getFactValue()))
                        .source(trimToNull(fact.getSource()))
                        .build())
                .forEach(skill -> mergeSkill(byName, skill));

        return byName.values().stream().limit(50).toList();
    }

    private List<AgentUserProfileDto.SkillEntry> parseCachedSkills(AgentUserProfile profile) {
        if (profile == null || !hasText(profile.getSkillProfileJson())) return List.of();
        try {
            return objectMapper.readValue(
                    profile.getSkillProfileJson(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, AgentUserProfileDto.SkillEntry.class));
        } catch (Exception exception) {
            log.warn("[career-bridge] invalid cached skill profile for student {}", profile.getUserId());
            return List.of();
        }
    }

    private void mergeSkill(Map<String, SkillSummary> byName, SkillSummary candidate) {
        if (candidate == null || !hasText(candidate.getName())) return;
        String key = candidate.getName().toLowerCase(Locale.ROOT);
        SkillSummary current = byName.get(key);
        if (current == null) {
            byName.put(key, candidate);
            return;
        }
        if (current.getLevel() == null) current.setLevel(candidate.getLevel());
        if (!hasText(current.getCategory())) current.setCategory(candidate.getCategory());
        if (!hasText(current.getSource())) current.setSource(candidate.getSource());
    }

    private String skillNameFromFact(UserFact fact) {
        if (fact == null || !hasText(fact.getFactKey())) return null;
        String name = fact.getFactKey()
                .replaceFirst("(?i)^weak_skill_", "")
                .replaceFirst("(?i)^skill_", "")
                .replace('_', ' ');
        return cleanSkillName(name);
    }

    private String cleanSkillName(String value) {
        String clean = trimToNull(value);
        if (clean == null) return null;
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }

    private Integer parseSkillLevel(String value) {
        if (!hasText(value)) return null;
        try {
            return validSkillLevel(Integer.valueOf(value.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer validSkillLevel(Integer value) {
        return value != null && value >= 0 && value <= 100 ? value : null;
    }

    private Behavior buildBehavior(
            CheckInService.CheckInStatus checkIn,
            List<AgentTask> tasks7d) {
        List<AgentTask> recent = nullSafeList(tasks7d);
        BigDecimal completionRate = null;
        if (!recent.isEmpty()) {
            long completed = recent.stream().filter(task -> "DONE".equals(task.getStatus())).count();
            completionRate = BigDecimal.valueOf(completed)
                    .divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);
        }
        return Behavior.builder()
                .streakDays(checkIn != null ? checkIn.getStreakDays() : null)
                .weeklyDays(checkIn != null ? checkIn.getWeeklyDays() : null)
                .completionRate7d(completionRate)
                .build();
    }

    private String latestUpdate(
            User user, UserProfileSnapshot snapshot, AgentUserProfile cachedProfile) {
        return Stream.of(
                        user.getUpdatedAt(),
                        snapshot.getUpdatedAt(),
                        cachedProfile != null ? cachedProfile.getUpdatedAt() : null)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(LocalDateTime::toString)
                .orElse(null);
    }

    private String validateTarget(String requested) {
        if (!hasText(requested)) return DEFAULT_AGENT_LINK;
        String target = requested.trim();
        int queryIndex = target.indexOf('?');
        String path = queryIndex >= 0 ? target.substring(0, queryIndex) : target;
        String query = queryIndex >= 0 ? target.substring(queryIndex + 1) : "";
        if (!ALLOWED_TARGET_PATHS.contains(path) || !SAFE_QUERY.matcher(query).matches()) {
            throw new BizException("target is not an allowed mini-program path");
        }
        return target;
    }

    private Map<String, Object> sanitizeEvidence(Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) return Map.of();
        Map<String, Object> safe = new LinkedHashMap<>();
        evidence.forEach((key, value) -> {
            if (safe.size() >= 20 || key == null
                    || !SAFE_EVIDENCE_KEY.matcher(key).matches()
                    || SENSITIVE_EVIDENCE_KEY.matcher(key).matches()
                    || value == null) {
                return;
            }
            if (value instanceof Number || value instanceof Boolean) {
                safe.put(key, value);
                return;
            }
            if (value instanceof CharSequence || value instanceof Character) {
                String text = String.valueOf(value);
                safe.put(key, text.length() <= 500 ? text : text.substring(0, 500));
            }
        });
        return safe;
    }

    private String notificationContent(InterventionRequest request) {
        if (hasText(request.getDescription())) return request.getDescription().trim();
        if (hasText(request.getSkill())) {
            return "建议优先提升「" + request.getSkill().trim() + "」，进入 AI 导师查看行动详情。";
        }
        return "进入 AI 导师查看这项行动的具体要求。";
    }

    private String redisKey(String code) {
        return LINK_CODE_KEY_PREFIX + code;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private <T> List<T> nullSafeList(List<T> value) {
        return value == null ? new ArrayList<>() : List.copyOf(value);
    }
}
