package com.group1.career.model.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Data-minimised contracts shared by the public linking endpoint and the
 * internal Career Platform bridge.
 */
public final class CareerBridgeDtos {

    private CareerBridgeDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkCodeResponse {
        private String code;
        private Integer expiresInSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumeLinkCodeRequest {
        @NotBlank
        @Pattern(regexp = "\\d{8}", message = "code must contain exactly 8 digits")
        private String code;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumeLinkCodeResponse {
        private Long studentId;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentPassportResponse {
        private String source;
        private Long studentId;
        private String displayName;
        private Education education;
        private String currentStage;
        private String targetRole;
        private Readiness readiness;
        private List<SkillSummary> skills;
        private Behavior behavior;
        private Evidence evidence;
        private List<TaskSummary> openTasks;
        private List<TaskSummary> recentInterventions;
        private String sourceUpdatedAt;
        private String generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Education {
        private String school;
        private String major;
        private Integer graduationYear;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Readiness {
        private Integer overallPercent;
        private Integer resumeScore;
        private Integer interviewScore;
        private Boolean hasAssessment;
        private Boolean hasResume;
        private Boolean hasInterview;
        private Boolean hasPlan;
        private Integer directionClarityPercent;
        private Integer resumeReadinessPercent;
        private Integer interviewReadinessPercent;
        private Integer actionContinuityPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private Integer resumeDiagnosisScore;
        private Integer lastInterviewScore;
        private List<String> interviewWeakDimensions;
        private Integer openTaskCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillSummary {
        private String name;
        private String category;
        private Integer level;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Behavior {
        private Integer streakDays;
        private Integer weeklyDays;
        private BigDecimal completionRate7d;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskSummary {
        private Long taskId;
        private String title;
        private String taskType;
        private String priority;
        private String status;
        private LocalDate dueDate;
        private String link;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterventionRequest {
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9._-]+",
                message = "idempotencyKey may contain only letters, digits, dot, underscore and hyphen")
        private String idempotencyKey;

        @NotBlank
        @Size(max = 160)
        private String title;

        @Size(max = 500)
        private String description;

        @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "priority must be LOW, MEDIUM or HIGH")
        private String priority;

        @FutureOrPresent
        private LocalDate dueDate;

        @Size(max = 100)
        private String skill;

        @Size(max = 20)
        private Map<String, Object> evidence;

        @Size(max = 255)
        private String target;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterventionResponse {
        private Boolean created;
        private TaskSummary task;
    }
}
