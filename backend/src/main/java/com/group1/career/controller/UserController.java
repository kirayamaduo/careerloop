package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.dto.UserProfileSnapshot;
import com.group1.career.service.AgentProfileService;
import com.group1.career.model.entity.User;
import com.group1.career.service.CareerPlanService;
import com.group1.career.service.UserProfileSnapshotService;
import com.group1.career.service.UserProfileTagService;
import com.group1.career.service.UserService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Tag(name = "User API")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileSnapshotService snapshotService;
    private final CareerPlanService careerPlanService;
    private final AgentProfileService agentProfileService;
    private final UserProfileTagService tagService;

    @Operation(summary = "Get user profile (presigned avatar URL hydrated)")
    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        return Result.success(userService.hydrateUrl(userService.getUserById(id)));
    }

    /**
     * Patch the caller's own profile. Any field omitted from the body is left
     * untouched. {@code avatarUrl} is the OSS object key returned by
     * {@code POST /api/files/upload?folder=avatars}.
     *
     * Cross-user updates are blocked: if {@code id} doesn't match the JWT
     * subject we throw FORBIDDEN. The previous version trusted the path
     * variable, which would have let any authenticated user overwrite any
     * profile.
     */
    @Operation(summary = "Update own profile (nickname / avatar / school / major / graduation year)")
    @PutMapping("/{id}")
    public Result<User> updateUser(@PathVariable Long id, @RequestBody UpdateUserDto dto) {
        Long uid = SecurityUtil.requireCurrentUserId();
        if (!uid.equals(id)) {
            throw new BizException(com.group1.career.common.ErrorCode.FORBIDDEN);
        }
        User updated = userService.updateUser(
                id, dto.getNickname(), dto.getAvatarUrl(),
                dto.getSchool(), dto.getMajor(), dto.getGraduationYear());
        if (dto.getSchool() != null || dto.getMajor() != null || dto.getGraduationYear() != null) {
            snapshotService.mergeOnboarding(uid, UserProfileSnapshot.OnboardingBlock.builder()
                    .education(UserProfileSnapshot.EducationBlock.builder()
                            .school(blankToNull(dto.getSchool()))
                            .major(blankToNull(dto.getMajor()))
                            .graduationYear(dto.getGraduationYear() != null
                                    ? String.valueOf(dto.getGraduationYear()) : null)
                            .build())
                    .build());
            tagService.refreshFromSignals(uid);
        }
        return Result.success(userService.hydrateUrl(updated));
    }

    /**
     * The cross-tool user portrait. Returned as the typed {@link UserProfileSnapshot}
     * (not the raw column) so the frontend never has to parse the JSON column itself
     * and gets nullable blocks for free. Always returns a snapshot, even an empty one,
     * so the caller never has to NPE-guard.
     */
    @Operation(summary = "Get the caller's cross-tool profile snapshot")
    @GetMapping("/me/profile-snapshot")
    public Result<UserProfileSnapshot> getMyProfileSnapshot() {
        Long uid = SecurityUtil.requireCurrentUserId();
        return Result.success(snapshotService.read(uid));
    }

    /**
     * Patch the caller's preferences block (target role / interview mode).
     * Only the fields present in the body are applied — useful when the
     * frontend records the user's chosen interview mode or wants to set
     * a target role from the assessment result CTA.
     */
    @Operation(summary = "Update preferences block in the profile snapshot")
    @PutMapping("/me/profile-snapshot/preferences")
    public Result<UserProfileSnapshot> updatePreferences(@RequestBody UpdatePreferencesDto dto) {
        Long uid = SecurityUtil.requireCurrentUserId();
        snapshotService.mergePreferences(uid, UserProfileSnapshot.PreferencesBlock.builder()
                .targetRole(dto.getTargetRole())
                .interviewMode(dto.getInterviewMode())
                .build());
        // When the user sets a new target role, kick off an async plan regeneration
        // so the AI career plan stays aligned without blocking this response.
        if (dto.getTargetRole() != null && !dto.getTargetRole().isBlank()) {
            careerPlanService.regenerateWithRoleAsync(uid, dto.getTargetRole());
        }
        return Result.success(snapshotService.read(uid));
    }

    @Operation(summary = "Update onboarding block in the profile snapshot")
    @PutMapping("/me/profile-snapshot/onboarding")
    public Result<UserProfileSnapshot> updateOnboarding(@RequestBody UpdateOnboardingDto dto) {
        Long uid = SecurityUtil.requireCurrentUserId();
        snapshotService.mergeOnboarding(uid, UserProfileSnapshot.OnboardingBlock.builder()
                .identityType(dto.getIdentityType())
                .stage(dto.getStage())
                .painPoint(dto.getPainPoint())
                .hasResume(dto.getHasResume())
                .resumeStatus(dto.getResumeStatus())
                .timeline(dto.getTimeline())
                .education(dto.getEducation())
                .weeklyAvailability(dto.getWeeklyAvailability())
                .priorityHelp(dto.getPriorityHelp())
                .recommendedEntry(dto.getRecommendedEntry())
                .onboardingCompletedAt(dto.getOnboardingCompletedAt())
                .build());

        if (dto.getTargetRole() != null) {
            snapshotService.mergePreferences(uid, UserProfileSnapshot.PreferencesBlock.builder()
                    .targetRole(dto.getTargetRole())
                    .build());
            if (!dto.getTargetRole().isBlank()) {
                careerPlanService.regenerateWithRoleAsync(uid, dto.getTargetRole());
            }
        }
        if (dto.getEducation() != null) {
            UserProfileSnapshot.EducationBlock edu = dto.getEducation();
            userService.updateUser(uid, null, null, blankToNull(edu.getSchool()), blankToNull(edu.getMajor()),
                    parseYear(edu.getGraduationYear()));
        }
        agentProfileService.refresh(uid);
        tagService.refreshFromSignals(uid);
        return Result.success(snapshotService.read(uid));
    }

    /**
     * F25: Request account deletion (soft-delete with 30-day grace period).
     * Sets deleted_at = now(). Subsequent authenticated requests return 410 Gone.
     * The user may cancel within 30 days via POST /users/me/cancel-deletion.
     */
    @Operation(summary = "Request account deletion (30-day grace period)")
    @DeleteMapping("/me")
    public Result<String> requestDeletion(HttpServletRequest request) {
        Long uid = SecurityUtil.requireCurrentUserId();
        userService.requestDeletion(uid, hashIp(request.getRemoteAddr()));
        return Result.success("Account deletion scheduled. You have 30 days to cancel.");
    }

    /**
     * F25: Cancel a pending deletion request (within the 30-day grace period).
     */
    @Operation(summary = "Cancel pending account deletion")
    @PostMapping("/me/cancel-deletion")
    public Result<String> cancelDeletion() {
        Long uid = SecurityUtil.requireCurrentUserId();
        userService.cancelDeletion(uid);
        return Result.success("Account deletion cancelled.");
    }

    private static String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((ip == null ? "" : ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer parseYear(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            int year = Integer.parseInt(value.trim());
            return year >= 1970 && year <= 2100 ? year : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Data
    public static class UpdateUserDto {
        private String nickname;
        /** OSS object key from /api/files/upload?folder=avatars */
        private String avatarUrl;
        private String school;
        private String major;
        private Integer graduationYear;
    }

    @Data
    public static class UpdatePreferencesDto {
        private String targetRole;
        /** "voice" or "text" */
        private String interviewMode;
    }

    @Data
    public static class UpdateOnboardingDto {
        /** student | new_graduate | internship_seeker | career_switcher */
        private String identityType;
        private String stage;
        private String painPoint;
        /** User self-reported resume state from onboarding: yes | no | unsure. */
        private String hasResume;
        private String resumeStatus;
        private String timeline;
        private UserProfileSnapshot.EducationBlock education;
        private String weeklyAvailability;
        private String priorityHelp;
        private String recommendedEntry;
        private String onboardingCompletedAt;
        /** Optional target role; persisted to preferences.targetRole, not duplicated in onboarding. */
        private String targetRole;
    }
}
