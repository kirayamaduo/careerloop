package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.CareerNode;
import com.group1.career.model.NotificationTypes;
import com.group1.career.service.NotificationService;
import com.group1.career.model.entity.CareerPath;
import com.group1.career.model.entity.Interview;
import com.group1.career.model.entity.InterviewQuestion;
import com.group1.career.model.entity.Organization;
import com.group1.career.model.entity.User;
import com.group1.career.repository.CareerNodeRepository;
import com.group1.career.repository.CareerPathRepository;
import com.group1.career.repository.InterviewQuestionRepository;
import com.group1.career.repository.InterviewRepository;
import com.group1.career.repository.OrganizationRepository;
import com.group1.career.repository.UserRepository;
import com.group1.career.service.AdminAuthService;
import com.group1.career.service.WeeklyReportService;
import com.group1.career.aspect.AuditLog;
import com.group1.career.model.entity.AdminAuditLog;
import com.group1.career.model.entity.HomeArticle;
import com.group1.career.model.entity.HomeVideo;
import com.group1.career.repository.AdminAuditLogRepository;
import com.group1.career.repository.AssessmentRecordRepository;
import com.group1.career.repository.CheckInRepository;
import com.group1.career.repository.HomeArticleRepository;
import com.group1.career.repository.HomeVideoRepository;
import com.group1.career.repository.UsageEventRepository;
import com.group1.career.utils.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B-side console endpoints. Every endpoint requires the caller to hold
 * the {@code ADMIN} role; we check via {@link AdminAuthService#requireAdmin}
 * rather than Spring Security {@code @PreAuthorize} (the rest of the app
 * uses a custom JWT pipeline so we keep the check inline).
 */
@Slf4j
@Tag(name = "Admin API", description = "Admin-only ops endpoints (Sprint D-4/D-5)")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final WeeklyReportService weeklyReportService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final CareerPathRepository careerPathRepository;
    private final CareerNodeRepository careerNodeRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final HomeVideoRepository homeVideoRepository;
    private final HomeArticleRepository homeArticleRepository;
    private final UsageEventRepository usageEventRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AssessmentRecordRepository assessmentRecordRepository;
    private final CheckInRepository checkInRepository;

    // ─────────────────── Auth-shaped utilities ───────────────────

    @Operation(summary = "Whoami — confirms the caller is recognised as an admin")
    @GetMapping("/whoami")
    public Result<Boolean> whoami() {
        Long uid = SecurityUtil.requireCurrentUserId();
        return Result.success(adminAuthService.isAdmin(uid));
    }

    // ─────────────────── Weekly report manual triggers ───────────────────

    @Operation(summary = "Manually trigger the weekly report job for every active user")
    @PostMapping("/weekly-report/run")
    public Result<WeeklyReportService.RunSummary> runWeeklyReport() {
        requireAdmin();
        return Result.success(weeklyReportService.runForAll());
    }

    @Operation(summary = "Manually trigger the weekly report for a specific user (debug)")
    @PostMapping("/weekly-report/run-user")
    public Result<Boolean> runWeeklyReportForUser(@RequestParam Long userId) {
        requireAdmin();
        return Result.success(weeklyReportService.runForUser(userId));
    }

    // ─────────────────── Organizations CRUD ───────────────────

    @Operation(summary = "List organizations")
    @GetMapping("/organizations")
    public Result<List<Organization>> listOrganizations() {
        requireAdmin();
        return Result.success(organizationRepository.findAll());
    }

    @Operation(summary = "Create or update an organization (upsert by code)")
    @PostMapping("/organizations")
    public Result<Organization> saveOrganization(@RequestBody Organization payload) {
        requireAdmin();
        if (payload.getCode() == null || payload.getCode().isBlank()) {
            throw new BizException("Organization code is required");
        }
        Organization existing = organizationRepository.findByCode(payload.getCode().trim()).orElse(null);
        if (existing != null) {
            existing.setName(payload.getName());
            existing.setDescription(payload.getDescription());
            existing.setContactName(payload.getContactName());
            existing.setContactEmail(payload.getContactEmail());
            if (payload.getActive() != null) existing.setActive(payload.getActive());
            return Result.success(organizationRepository.save(existing));
        }
        payload.setCode(payload.getCode().trim());
        if (payload.getActive() == null) payload.setActive(true);
        return Result.success(organizationRepository.save(payload));
    }

    // ─────────────────── Org dashboard ───────────────────

    @Operation(summary = "Org dashboard — radar averages, interview counts, weak dimensions")
    @GetMapping("/organizations/{orgId}/dashboard")
    public Result<OrgDashboardDto> orgDashboard(@PathVariable Long orgId) {
        requireAdmin();
        organizationRepository.findById(orgId)
                .orElseThrow(() -> new BizException("Organization not found"));

        List<User> students = userRepository.findByOrgId(orgId);
        Map<String, Double[]> dimSums = new HashMap<>();
        for (String d : RADAR_DIMENSIONS) dimSums.put(d, new Double[]{0.0, 0.0}); // sum, count
        int interviewCount = 0;
        int reportCount = 0;
        for (User u : students) {
            for (Interview iv : interviewRepository.findByUserIdOrderByStartedAtDesc(u.getUserId())) {
                interviewCount++;
                if (iv.getReportJson() == null || iv.getReportJson().isBlank()) continue;
                try {
                    JsonNode radar = objectMapper.readTree(iv.getReportJson()).get("radarChart");
                    if (radar == null || !radar.isObject()) continue;
                    reportCount++;
                    for (String d : RADAR_DIMENSIONS) {
                        JsonNode v = radar.get(d);
                        if (v == null || !v.isNumber()) continue;
                        Double[] cell = dimSums.get(d);
                        cell[0] += v.asDouble();
                        cell[1] += 1;
                    }
                } catch (Exception e) {
                    log.debug("[admin] could not parse report for interview {}: {}", iv.getInterviewId(), e.toString());
                }
            }
        }
        Map<String, Double> averages = new HashMap<>();
        for (String d : RADAR_DIMENSIONS) {
            Double[] cell = dimSums.get(d);
            averages.put(d, cell[1] == 0 ? 0.0 : Math.round((cell[0] / cell[1]) * 10.0) / 10.0);
        }
        // Weak dimensions = lowest 3 averages.
        List<String> weak = new ArrayList<>(averages.keySet());
        weak.sort((a, b) -> Double.compare(averages.get(a), averages.get(b)));
        List<String> top3Weak = weak.size() > 3 ? weak.subList(0, 3) : weak;

        return Result.success(OrgDashboardDto.builder()
                .orgId(orgId)
                .studentCount(students.size())
                .interviewCount(interviewCount)
                .reportCount(reportCount)
                .radarAverages(averages)
                .weakDimensionsTop3(top3Weak)
                .build());
    }

    @Operation(summary = "List students in an org with their interview counts and last score")
    @GetMapping("/organizations/{orgId}/students")
    public Result<List<StudentRowDto>> orgStudents(@PathVariable Long orgId) {
        requireAdmin();
        List<StudentRowDto> rows = new ArrayList<>();
        for (User u : userRepository.findByOrgId(orgId)) {
            List<Interview> ivs = interviewRepository.findByUserIdOrderByStartedAtDesc(u.getUserId());
            Integer lastScore = ivs.stream()
                    .filter(i -> i.getFinalScore() != null)
                    .findFirst()
                    .map(Interview::getFinalScore)
                    .orElse(null);
            rows.add(StudentRowDto.builder()
                    .userId(u.getUserId())
                    .nickname(u.getNickname())
                    .school(u.getSchool())
                    .major(u.getMajor())
                    .interviewCount(ivs.size())
                    .lastInterviewScore(lastScore)
                    .build());
        }
        return Result.success(rows);
    }

    @Operation(summary = "Student detail — full interview list with scores")
    @GetMapping("/students/{userId}")
    public Result<StudentDetailDto> studentDetail(@PathVariable Long userId) {
        requireAdmin();
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("Student not found"));
        return Result.success(StudentDetailDto.builder()
                .user(u)
                .interviews(interviewRepository.findByUserIdOrderByStartedAtDesc(userId))
                .build());
    }

    // ─────────────────── Skill map editor ───────────────────

    @Operation(summary = "List all career paths (admin)")
    @GetMapping("/career-paths")
    public Result<List<CareerPath>> listPaths() {
        requireAdmin();
        return Result.success(careerPathRepository.findAll());
    }

    @Operation(summary = "Create or update a career path (upsert by id; new on null)")
    @PostMapping("/career-paths")
    public Result<CareerPath> savePath(@RequestBody CareerPath payload) {
        requireAdmin();
        return Result.success(careerPathRepository.save(payload));
    }

    @Operation(summary = "Delete a career path")
    @DeleteMapping("/career-paths/{pathId}")
    public Result<Void> deletePath(@PathVariable Integer pathId) {
        requireAdmin();
        careerPathRepository.deleteById(pathId);
        return Result.success();
    }

    @Operation(summary = "List nodes in a path (admin — full tree, no progress join)")
    @GetMapping("/career-paths/{pathId}/nodes")
    public Result<List<CareerNode>> listNodes(@PathVariable Integer pathId) {
        requireAdmin();
        return Result.success(careerNodeRepository.findByPathIdOrderBySortOrderAsc(pathId));
    }

    @Operation(summary = "Create or update a node")
    @PostMapping("/career-paths/{pathId}/nodes")
    public Result<CareerNode> saveNode(@PathVariable Integer pathId, @RequestBody CareerNode payload) {
        requireAdmin();
        payload.setPathId(pathId);
        return Result.success(careerNodeRepository.save(payload));
    }

    @Operation(summary = "Delete a node")
    @DeleteMapping("/career-paths/nodes/{nodeId}")
    public Result<Void> deleteNode(@PathVariable Long nodeId) {
        requireAdmin();
        careerNodeRepository.deleteById(nodeId);
        return Result.success();
    }

    // ─────────────────── Question bank moderation ───────────────────

    @Operation(summary = "List all questions (admin — includes HIDDEN status). Optional ?source=AI_GENERATED&reviewStatus=PENDING_REVIEW filter.")
    @GetMapping("/questions")
    public Result<List<InterviewQuestion>> listQuestions(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String reviewStatus) {
        requireAdmin();
        List<InterviewQuestion> all = interviewQuestionRepository.findAll();
        return Result.success(all.stream()
                .filter(q -> source == null || source.equals(q.getSource()))
                .filter(q -> reviewStatus == null || reviewStatus.equals(q.getReviewStatus()))
                .collect(java.util.stream.Collectors.toList()));
    }

    @Operation(summary = "Update a question (status, content, position, difficulty)")
    @PostMapping("/questions/{id}")
    public Result<InterviewQuestion> updateQuestion(@PathVariable Long id, @RequestBody InterviewQuestion payload) {
        requireAdmin();
        InterviewQuestion existing = interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new BizException("Question not found"));
        if (payload.getContent() != null) existing.setContent(payload.getContent());
        if (payload.getSummary() != null) existing.setSummary(payload.getSummary());
        if (payload.getPosition() != null) existing.setPosition(payload.getPosition());
        if (payload.getDifficulty() != null) existing.setDifficulty(payload.getDifficulty());
        if (payload.getStatus() != null) existing.setStatus(payload.getStatus());
        if (payload.getReviewStatus() != null) existing.setReviewStatus(payload.getReviewStatus());
        if (payload.getAnswer() != null) existing.setAnswer(payload.getAnswer());
        return Result.success(interviewQuestionRepository.save(existing));
    }

    @Operation(summary = "Approve an AI-generated question (sets reviewStatus=PUBLISHED)")
    @PostMapping("/questions/{id}/approve")
    @AuditLog(action = "APPROVE_QUESTION", targetType = "QUESTION")
    public Result<InterviewQuestion> approveQuestion(@PathVariable Long id) {
        requireAdmin();
        InterviewQuestion q = interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new BizException("Question not found"));
        q.setReviewStatus("PUBLISHED");
        return Result.success(interviewQuestionRepository.save(q));
    }

    @Operation(summary = "Reject an AI-generated question (sets reviewStatus=REJECTED)")
    @PostMapping("/questions/{id}/reject")
    @AuditLog(action = "REJECT_QUESTION", targetType = "QUESTION")
    public Result<InterviewQuestion> rejectQuestion(@PathVariable Long id) {
        requireAdmin();
        InterviewQuestion q = interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new BizException("Question not found"));
        q.setReviewStatus("REJECTED");
        return Result.success(interviewQuestionRepository.save(q));
    }

    @Operation(summary = "Delete a question")
    @DeleteMapping("/questions/{id}")
    @AuditLog(action = "DELETE_QUESTION", targetType = "QUESTION")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        requireAdmin();
        interviewQuestionRepository.deleteById(id);
        return Result.success();
    }

    // ─────────────────── F16: User management ───────────────────

    @Operation(summary = "List all non-deleted users (paginated, optional nickname search)")
    @GetMapping("/users")
    public Result<Page<User>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        requireAdmin();
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = (q != null && !q.isBlank())
                ? userRepository.searchByNickname(q.trim(), pr)
                : userRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc(pr);
        return Result.success(result);
    }

    @Operation(summary = "Get full user detail (admin)")
    @GetMapping("/users/{userId}")
    public Result<User> userDetail(@PathVariable Long userId) {
        requireAdmin();
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("User not found"));
        return Result.success(u);
    }

    @Operation(summary = "Ban a user — sets status=2 and records ban reason")
    @PostMapping("/users/{userId}/ban")
    @AuditLog(action = "BAN_USER", targetType = "USER")
    public Result<User> banUser(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        requireAdmin();
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("User not found"));
        String reason = body.getOrDefault("reason", "Violated community guidelines");
        u.setStatus(2);
        u.setBannedReason(reason);
        u.setAuthVersion(nextAuthVersion(u));
        User saved = userRepository.save(u);
        notificationService.push(userId,
                NotificationTypes.SYSTEM,
                "Account suspended",
                "Your account has been suspended. Reason: " + reason,
                null);
        log.info("[admin] user {} banned by admin {}: {}", userId, SecurityUtil.currentUserId(), reason);
        return Result.success(saved);
    }

    @Operation(summary = "Unban a user — restores status=1 and clears ban reason")
    @PostMapping("/users/{userId}/unban")
    @AuditLog(action = "UNBAN_USER", targetType = "USER")
    public Result<User> unbanUser(@PathVariable Long userId) {
        requireAdmin();
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("User not found"));
        u.setStatus(1);
        u.setBannedReason(null);
        // Tokens issued before or during a ban must stay unusable after unban.
        u.setAuthVersion(nextAuthVersion(u));
        User saved = userRepository.save(u);
        notificationService.push(userId,
                NotificationTypes.SYSTEM,
                "Account restored",
                "Your account restriction has been lifted. Welcome back!",
                null);
        log.info("[admin] user {} unbanned by admin {}", userId, SecurityUtil.currentUserId());
        return Result.success(saved);
    }

    @Operation(summary = "Send admin broadcast to a single user or all active users")
    @PostMapping("/broadcast")
    @AuditLog(action = "BROADCAST", targetType = "NOTIFICATION", idParamIndex = -1)
    public Result<Integer> broadcast(@RequestBody BroadcastRequest req) {
        requireAdmin();
        if (req.getTitle() == null || req.getTitle().isBlank()) throw new BizException("Title required");
        if (req.getContent() == null || req.getContent().isBlank()) throw new BizException("Content required");
        int count = 0;
        if (req.getUserId() != null) {
            notificationService.push(req.getUserId(), NotificationTypes.ADMIN_BROADCAST,
                    req.getTitle(), req.getContent(), req.getLink());
            count = 1;
        } else {
            List<User> active = userRepository.findAll().stream()
                    .filter(u -> u.getDeletedAt() == null && u.getStatus() != null && u.getStatus() == 1)
                    .toList();
            for (User u : active) {
                notificationService.push(u.getUserId(), NotificationTypes.ADMIN_BROADCAST,
                        req.getTitle(), req.getContent(), req.getLink());
                count++;
            }
        }
        log.info("[admin] broadcast sent to {} users by admin {}", count, SecurityUtil.currentUserId());
        return Result.success(count);
    }

    // ─────────────────── F17: Content management ───────────────────

    @Operation(summary = "List home videos (paginated, newest-first)")
    @GetMapping("/content/videos")
    public Result<Page<HomeVideo>> listVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fetchedAt"));
        return Result.success(homeVideoRepository.findAll(pr));
    }

    @Operation(summary = "Delete a home video")
    @DeleteMapping("/content/videos/{id}")
    @AuditLog(action = "DELETE_VIDEO", targetType = "HOME_VIDEO")
    public Result<Void> deleteVideo(@PathVariable Long id) {
        requireAdmin();
        homeVideoRepository.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "List all home articles (admin view, newest first)")
    @GetMapping("/content/articles")
    public Result<List<HomeArticle>> listArticles() {
        requireAdmin();
        return Result.success(homeArticleRepository.findAll(
                Sort.by(Sort.Direction.DESC, "publishedAt")));
    }

    @Operation(summary = "Create a home article (manual entry)")
    @PostMapping("/content/articles")
    @AuditLog(action = "CREATE_ARTICLE", targetType = "HOME_ARTICLE", idParamIndex = -1)
    public Result<HomeArticle> createArticle(@RequestBody HomeArticle payload) {
        requireAdmin();
        payload.setId(null);
        if (payload.getPublishedAt() == null) payload.setPublishedAt(LocalDateTime.now());
        return Result.success(homeArticleRepository.save(payload));
    }

    @Operation(summary = "Update a home article")
    @PutMapping("/content/articles/{id}")
    @AuditLog(action = "UPDATE_ARTICLE", targetType = "HOME_ARTICLE")
    public Result<HomeArticle> updateArticle(@PathVariable Long id, @RequestBody HomeArticle payload) {
        requireAdmin();
        HomeArticle existing = homeArticleRepository.findById(id)
                .orElseThrow(() -> new BizException("Article not found"));
        if (payload.getTitle() != null) existing.setTitle(payload.getTitle());
        if (payload.getSummary() != null) existing.setSummary(payload.getSummary());
        if (payload.getImageUrl() != null) existing.setImageUrl(payload.getImageUrl());
        if (payload.getSourceUrl() != null) existing.setSourceUrl(payload.getSourceUrl());
        if (payload.getCategory() != null) existing.setCategory(payload.getCategory());
        if (payload.getPublishedAt() != null) existing.setPublishedAt(payload.getPublishedAt());
        return Result.success(homeArticleRepository.save(existing));
    }

    @Operation(summary = "Delete a home article")
    @DeleteMapping("/content/articles/{id}")
    @AuditLog(action = "DELETE_ARTICLE", targetType = "HOME_ARTICLE")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        requireAdmin();
        homeArticleRepository.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "Toggle pin status of a home article")
    @PatchMapping("/content/articles/{id}/pin")
    @AuditLog(action = "TOGGLE_PIN_ARTICLE", targetType = "HOME_ARTICLE")
    public Result<HomeArticle> togglePin(@PathVariable Long id) {
        requireAdmin();
        HomeArticle article = homeArticleRepository.findById(id)
                .orElseThrow(() -> new BizException("Article not found"));
        article.setPinned(!Boolean.TRUE.equals(article.getPinned()));
        return Result.success(homeArticleRepository.save(article));
    }

    @Operation(summary = "Toggle hidden status of a home article")
    @PatchMapping("/content/articles/{id}/hide")
    @AuditLog(action = "TOGGLE_HIDE_ARTICLE", targetType = "HOME_ARTICLE")
    public Result<HomeArticle> toggleHide(@PathVariable Long id) {
        requireAdmin();
        HomeArticle article = homeArticleRepository.findById(id)
                .orElseThrow(() -> new BizException("Article not found"));
        article.setHidden(!Boolean.TRUE.equals(article.getHidden()));
        return Result.success(homeArticleRepository.save(article));
    }

    // ─────────────────── F18: Analytics ───────────────────

    @Operation(summary = "Analytics summary — platform totals + 30-day event breakdown")
    @GetMapping("/analytics/summary")
    public Result<AnalyticsSummaryDto> analyticsSummary() {
        requireAdmin();
        LocalDateTime since30 = LocalDateTime.now().minusDays(30);

        long totalUsers       = userRepository.count();
        long totalInterviews  = interviewRepository.count();
        long totalAssessments = assessmentRecordRepository.count();
        long totalCheckIns    = checkInRepository.count();

        Map<String, Long> eventBreakdown = new LinkedHashMap<>();
        for (Object[] row : usageEventRepository.countByEventTypeSince(since30)) {
            eventBreakdown.put((String) row[0], (Long) row[1]);
        }

        return Result.success(AnalyticsSummaryDto.builder()
                .totalUsers(totalUsers)
                .totalInterviews(totalInterviews)
                .totalAssessments(totalAssessments)
                .totalCheckIns(totalCheckIns)
                .eventBreakdown30d(eventBreakdown)
                .build());
    }

    // ─────────────────── F19: Audit log ───────────────────

    @Operation(summary = "Admin audit log (paginated, newest first)")
    @GetMapping("/audit-log")
    public Result<Page<AdminAuditLog>> listAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        requireAdmin();
        return Result.success(adminAuditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size)));
    }

    // ─────────────────── Helpers + DTOs ───────────────────

    private static final List<String> RADAR_DIMENSIONS = List.of(
            "expression", "logic", "technical", "pressureResistance", "communication"
    );

    private void requireAdmin() {
        Long uid = SecurityUtil.requireCurrentUserId();
        adminAuthService.requireAdmin(uid);
    }

    private long nextAuthVersion(User user) {
        return (user.getAuthVersion() == null ? 0L : user.getAuthVersion()) + 1L;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BroadcastRequest {
        private Long userId;
        private String title;
        private String content;
        private String link;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class OrgDashboardDto {
        private Long orgId;
        private int studentCount;
        private int interviewCount;
        private int reportCount;
        private Map<String, Double> radarAverages;
        private List<String> weakDimensionsTop3;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class StudentRowDto {
        private Long userId;
        private String nickname;
        private String school;
        private String major;
        private int interviewCount;
        private Integer lastInterviewScore;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class AnalyticsSummaryDto {
        private long totalUsers;
        private long totalInterviews;
        private long totalAssessments;
        private long totalCheckIns;
        private Map<String, Long> eventBreakdown30d;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class StudentDetailDto {
        private User user;
        private List<Interview> interviews;
    }
}
