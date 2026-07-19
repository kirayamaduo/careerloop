package com.group1.career.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.model.dto.UserProfileSnapshot;
import com.group1.career.model.entity.User;
import com.group1.career.model.entity.UserFact;
import com.group1.career.model.entity.Resume;
import com.group1.career.repository.ResumeRepository;
import com.group1.career.repository.UserFactRepository;
import com.group1.career.repository.UserRepository;
import com.group1.career.service.UserProfileSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileSnapshotServiceImpl implements UserProfileSnapshotService {

    private static final DateTimeFormatter PROMPT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserRepository userRepository;
    private final UserFactRepository userFactRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;

    @Override
    public UserProfileSnapshot read(Long userId) {
        if (userId == null) return UserProfileSnapshot.builder().build();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return UserProfileSnapshot.builder().build();
        UserProfileSnapshot snapshot = parse(userOpt.get().getProfileSnapshot());
        enrichOnboardingFromFacts(userId, snapshot);
        try {
            reconcileResumeBlock(userId, snapshot);
        } catch (Exception e) {
            log.warn("[snapshot] resume reconcile failed for user {}: {}", userId, e.toString());
        }
        return snapshot;
    }

    /**
     * Fill empty onboarding fields from UserFacts so the homepage "求职画像"
     * card reflects the latest user-edited values even when the onboarding
     * block was written before those edits. This is a read-time overlay —
     * it does NOT persist changes back to the snapshot column.
     */
    private void enrichOnboardingFromFacts(Long userId, UserProfileSnapshot snapshot) {
        UserProfileSnapshot.OnboardingBlock onboarding = snapshot.getOnboarding();
        if (onboarding == null) {
            onboarding = UserProfileSnapshot.OnboardingBlock.builder().build();
            snapshot.setOnboarding(onboarding);
        }

        // Only fetch facts when at least one field could benefit
        boolean needsTimeline = !hasText(onboarding.getTimeline());
        boolean needsWeekly = !hasText(onboarding.getWeeklyAvailability());
        boolean needsEducation = onboarding.getEducation() == null
                || (!hasText(onboarding.getEducation().getSchool()) && !hasText(onboarding.getEducation().getMajor()));
        if (!needsTimeline && !needsWeekly && !needsEducation) return;

        List<UserFact> facts = userFactRepository.findByUserId(userId);
        Map<String, String> factMap = facts.stream()
                .filter(f -> hasText(f.getFactValue()))
                .collect(Collectors.toMap(UserFact::getFactKey, UserFact::getFactValue, (a, b) -> b));

        if (needsTimeline && factMap.containsKey("timeline")) {
            onboarding.setTimeline(mapTimelineToOnboarding(factMap.get("timeline")));
        }
        if (needsWeekly && factMap.containsKey("weekly_hours")) {
            onboarding.setWeeklyAvailability(mapWeeklyHoursToOnboarding(factMap.get("weekly_hours")));
        }
        if (needsEducation) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && (hasText(user.getSchool()) || hasText(user.getMajor()))) {
                UserProfileSnapshot.EducationBlock edu = onboarding.getEducation();
                if (edu == null) {
                    edu = UserProfileSnapshot.EducationBlock.builder().build();
                    onboarding.setEducation(edu);
                }
                if (!hasText(edu.getSchool()) && hasText(user.getSchool())) edu.setSchool(user.getSchool());
                if (!hasText(edu.getMajor()) && hasText(user.getMajor())) edu.setMajor(user.getMajor());
            }
        }
    }

    private static String mapWeeklyHoursToOnboarding(String hours) {
        return switch (hours) {
            case "3"  -> "lt_5h";
            case "7"  -> "5_10h";
            case "15" -> "10_20h";
            case "25" -> "gt_20h";
            default   -> hours;
        };
    }

    private static String mapTimelineToOnboarding(String timeline) {
        if (timeline == null || timeline.isBlank()) return timeline;
        return switch (timeline.trim()) {
            case "1个月" -> "within_1_month";
            case "3个月" -> "within_3_months";
            case "6个月", "校招季" -> "prepare_early";
            default -> timeline;
        };
    }

    @Override
    public String renderForPrompt(Long userId) {
        if (userId == null) return "";
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return "";

        User user = userOpt.get();
        UserProfileSnapshot snap = parse(user.getProfileSnapshot());
        boolean hasBasicProfile = hasText(user.getNickname())
                || hasText(user.getSchool())
                || hasText(user.getMajor())
                || user.getGraduationYear() != null;
        if ((snap == null || isEmpty(snap)) && !hasBasicProfile) return "";

        List<String> lines = new ArrayList<>();
        lines.add("Known about this user (from prior tool usage — be specific, never claim ignorance of these):");

        if (hasBasicProfile) {
            StringBuilder sb = new StringBuilder("- Basic profile: ");
            if (hasText(user.getNickname())) sb.append("nickname ").append(user.getNickname());
            if (hasText(user.getSchool())) {
                if (sb.length() > "- Basic profile: ".length()) sb.append("; ");
                sb.append("school ").append(user.getSchool());
            }
            if (hasText(user.getMajor())) {
                if (sb.length() > "- Basic profile: ".length()) sb.append("; ");
                sb.append("major ").append(user.getMajor());
            }
            if (user.getGraduationYear() != null) {
                if (sb.length() > "- Basic profile: ".length()) sb.append("; ");
                sb.append("graduation year ").append(user.getGraduationYear());
            }
            lines.add(sb.toString());
        }

        if (snap.getAssessment() != null) {
            UserProfileSnapshot.AssessmentBlock a = snap.getAssessment();
            StringBuilder sb = new StringBuilder("- Assessment: ");
            if (a.getScaleTitle() != null) sb.append(a.getScaleTitle()).append(" → ");
            if (a.getSummary() != null) sb.append("type ").append(a.getSummary());
            if (a.getSuggestedRoles() != null && !a.getSuggestedRoles().isEmpty()) {
                sb.append("; AI-suggested roles: ").append(String.join(", ", a.getSuggestedRoles()));
            }
            if (a.getCompletedAt() != null) sb.append(" (").append(a.getCompletedAt().format(PROMPT_DATE_FMT)).append(")");
            lines.add(sb.toString());
        }

        if (snap.getResume() != null) {
            UserProfileSnapshot.ResumeBlock r = snap.getResume();
            StringBuilder sb = new StringBuilder("- Resume: ");
            if (r.getTitle() != null) sb.append("\"").append(r.getTitle()).append("\" ");
            if (r.getTargetJob() != null) sb.append("targeting ").append(r.getTargetJob());
            if (r.getDiagnosisScore() != null && r.getDiagnosisScore() > 0) {
                sb.append("; diagnosis score ").append(r.getDiagnosisScore()).append("/100");
            }
            if (r.getUpdatedAt() != null) sb.append(" (").append(r.getUpdatedAt().format(PROMPT_DATE_FMT)).append(")");
            lines.add(sb.toString());
        }

        if (snap.getInterview() != null) {
            UserProfileSnapshot.InterviewBlock i = snap.getInterview();
            StringBuilder sb = new StringBuilder("- Last interview: ");
            if (i.getPositionName() != null) sb.append(i.getPositionName());
            if (i.getDifficulty() != null) sb.append(" (").append(i.getDifficulty()).append(")");
            if (i.getLastScore() != null) sb.append(", score ").append(i.getLastScore()).append("/100");
            if (i.getStrongDimensions() != null && !i.getStrongDimensions().isEmpty()) {
                sb.append("; strengths: ").append(String.join(", ", i.getStrongDimensions()));
            }
            if (i.getWeakDimensions() != null && !i.getWeakDimensions().isEmpty()) {
                sb.append("; growth areas: ").append(String.join(", ", i.getWeakDimensions()));
            }
            if (i.getCompletedAt() != null) sb.append(" (").append(i.getCompletedAt().format(PROMPT_DATE_FMT)).append(")");
            lines.add(sb.toString());
        }

        if (snap.getPreferences() != null) {
            UserProfileSnapshot.PreferencesBlock p = snap.getPreferences();
            if (p.getTargetRole() != null) {
                lines.add("- Target role of interest: " + p.getTargetRole());
            }
        }

        if (snap.getOnboarding() != null) {
            UserProfileSnapshot.OnboardingBlock o = snap.getOnboarding();
            StringBuilder sb = new StringBuilder("- Onboarding setup: ");
            if (hasText(o.getIdentityType())) sb.append("identity ").append(o.getIdentityType());
            if (hasText(o.getPainPoint())) {
                if (sb.length() > "- Onboarding setup: ".length()) sb.append("; ");
                sb.append("pain point ").append(o.getPainPoint());
            }
            if (hasText(o.getHasResume())) {
                if (sb.length() > "- Onboarding setup: ".length()) sb.append("; ");
                sb.append("self-reported resume state ").append(o.getHasResume());
            }
            if (hasText(o.getTimeline())) {
                if (sb.length() > "- Onboarding setup: ".length()) sb.append("; ");
                sb.append("timeline ").append(o.getTimeline());
            }
            if (hasText(o.getWeeklyAvailability())) {
                if (sb.length() > "- Onboarding setup: ".length()) sb.append("; ");
                sb.append("weekly availability ").append(o.getWeeklyAvailability());
            }
            if (hasText(o.getPriorityHelp())) {
                if (sb.length() > "- Onboarding setup: ".length()) sb.append("; ");
                sb.append("priority help ").append(o.getPriorityHelp());
            }
            if (hasText(o.getOnboardingCompletedAt())) {
                if (sb.length() > "- Onboarding setup: ".length()) sb.append("; ");
                sb.append("completed at ").append(o.getOnboardingCompletedAt());
            }
            if (sb.length() > "- Onboarding setup: ".length()) lines.add(sb.toString());
        }

        return String.join("\n", lines);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeAssessment(Long userId, UserProfileSnapshot.AssessmentBlock block) {
        if (userId == null || block == null) return;
        UserProfileSnapshot current = read(userId);
        current.setAssessment(block);
        // Bubble suggested role up to preferences if the user doesn't have an
        // explicit one yet — that's how the assessment becomes a portal into
        // resume diagnosis / interview start without an extra UI step.
        if (block.getSuggestedRoles() != null && !block.getSuggestedRoles().isEmpty()) {
            UserProfileSnapshot.PreferencesBlock prefs = current.getPreferences();
            if (prefs == null) prefs = UserProfileSnapshot.PreferencesBlock.builder().build();
            if (prefs.getTargetRole() == null || prefs.getTargetRole().isBlank()) {
                prefs.setTargetRole(block.getSuggestedRoles().get(0));
                current.setPreferences(prefs);
            }
        }
        persist(userId, current);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeResume(Long userId, UserProfileSnapshot.ResumeBlock block) {
        if (userId == null || block == null) return;
        UserProfileSnapshot current = read(userId);
        current.setResume(block);
        persist(userId, current);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearResume(Long userId) {
        if (userId == null) return;
        UserProfileSnapshot current = read(userId);
        current.setResume(null);
        persist(userId, current);
    }

    /**
     * Drop stale resume portrait data when the referenced row no longer exists,
     * or back-fill from the newest DB row when the block is orphaned.
     */
    private void reconcileResumeBlock(Long userId, UserProfileSnapshot snapshot) {
        UserProfileSnapshot.ResumeBlock block = snapshot.getResume();
        List<Resume> resumes = resumeRepository.findByUserId(userId);
        if (resumes.isEmpty()) {
            if (block != null) snapshot.setResume(null);
            return;
        }

        boolean valid = block != null
                && block.getLastResumeId() != null
                && resumes.stream().anyMatch(r -> block.getLastResumeId().equals(r.getResumeId()));
        if (valid) return;

        Resume latest = resumes.stream()
                .max(java.util.Comparator.comparing(
                        (Resume r) -> r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt(),
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElse(resumes.get(0));
        snapshot.setResume(UserProfileSnapshot.ResumeBlock.builder()
                .lastResumeId(latest.getResumeId())
                .lastResumeKey(latest.getFileUrl())
                .title(latest.getTitle())
                .targetJob(latest.getTargetJob())
                .diagnosisScore(latest.getDiagnosisScore())
                .updatedAt(latest.getUpdatedAt())
                .build());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeInterview(Long userId, UserProfileSnapshot.InterviewBlock block) {
        if (userId == null || block == null) return;
        UserProfileSnapshot current = read(userId);
        current.setInterview(block);
        persist(userId, current);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergePreferences(Long userId, UserProfileSnapshot.PreferencesBlock block) {
        if (userId == null || block == null) return;
        UserProfileSnapshot current = read(userId);
        UserProfileSnapshot.PreferencesBlock existing = current.getPreferences();
        if (existing == null) {
            current.setPreferences(block);
        } else {
            // Field-by-field merge so we don't wipe earlier preferences
            // when only one field is being updated this time.
            if (block.getTargetRole() != null) existing.setTargetRole(block.getTargetRole());
            if (block.getInterviewMode() != null) existing.setInterviewMode(block.getInterviewMode());
            current.setPreferences(existing);
        }
        persist(userId, current);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeOnboarding(Long userId, UserProfileSnapshot.OnboardingBlock block) {
        if (userId == null || block == null) return;
        UserProfileSnapshot current = read(userId);
        UserProfileSnapshot.OnboardingBlock existing = current.getOnboarding();
        if (existing == null) {
            current.setOnboarding(block);
        } else {
            if (block.getIdentityType() != null) existing.setIdentityType(block.getIdentityType());
            if (block.getStage() != null) existing.setStage(block.getStage());
            if (block.getPainPoint() != null) existing.setPainPoint(block.getPainPoint());
            if (block.getHasResume() != null) existing.setHasResume(block.getHasResume());
            if (block.getResumeStatus() != null) existing.setResumeStatus(block.getResumeStatus());
            if (block.getTimeline() != null) existing.setTimeline(block.getTimeline());
            if (block.getEducation() != null) {
                existing.setEducation(mergeEducation(existing.getEducation(), block.getEducation()));
            }
            if (block.getWeeklyAvailability() != null) existing.setWeeklyAvailability(block.getWeeklyAvailability());
            if (block.getPriorityHelp() != null) existing.setPriorityHelp(block.getPriorityHelp());
            if (block.getRecommendedEntry() != null) existing.setRecommendedEntry(block.getRecommendedEntry());
            if (block.getOnboardingCompletedAt() != null) existing.setOnboardingCompletedAt(block.getOnboardingCompletedAt());
            current.setOnboarding(existing);
        }
        persist(userId, current);
    }

    /**
     * Education is itself a patchable block. A null field means "not part of
     * this update", while an empty string is retained as an explicit clear.
     * This prevents profile edits (which do not expose degree) from erasing
     * degree or any other onboarding-only education signal.
     */
    private static UserProfileSnapshot.EducationBlock mergeEducation(
            UserProfileSnapshot.EducationBlock existing,
            UserProfileSnapshot.EducationBlock patch) {
        if (existing == null) return patch;
        if (patch.getSchool() != null) existing.setSchool(patch.getSchool());
        if (patch.getMajor() != null) existing.setMajor(patch.getMajor());
        if (patch.getDegree() != null) existing.setDegree(patch.getDegree());
        if (patch.getGraduationYear() != null) existing.setGraduationYear(patch.getGraduationYear());
        return existing;
    }

    /**
     * Best-effort persistence — snapshot writes are never on the user's
     * critical path (they happen after the real assessment / resume /
     * interview write committed), so we swallow failures and log them
     * loudly rather than failing the originating request.
     */
    private void persist(Long userId, UserProfileSnapshot snap) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("[snapshot] user {} not found, skipping merge", userId);
                return;
            }
            if (snap.getVersion() == null) snap.setVersion(1);
            snap.setUpdatedAt(LocalDateTime.now());
            user.setProfileSnapshot(objectMapper.writeValueAsString(snap));
            userRepository.save(user);
            log.debug("[snapshot] persisted for user {}", userId);
        } catch (Exception e) {
            log.warn("[snapshot] persist failed for user {}: {}", userId, e.toString());
        }
    }

    private UserProfileSnapshot parse(String json) {
        if (json == null || json.isBlank()) return UserProfileSnapshot.builder().build();
        try {
            UserProfileSnapshot s = objectMapper.readValue(json, UserProfileSnapshot.class);
            return s == null ? UserProfileSnapshot.builder().build() : s;
        } catch (Exception e) {
            log.warn("[snapshot] failed to parse profile_snapshot, returning empty: {}", e.toString());
            return UserProfileSnapshot.builder().build();
        }
    }

    private boolean isEmpty(UserProfileSnapshot s) {
        return s.getAssessment() == null
                && s.getResume() == null
                && s.getInterview() == null
                && s.getPreferences() == null
                && s.getOnboarding() == null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
