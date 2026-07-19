package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.model.entity.CareerNode;
import com.group1.career.model.entity.CareerPath;
import com.group1.career.model.entity.UserCareerPlan;
import com.group1.career.model.entity.UserCareerProgress;
import com.group1.career.service.AdminAuthService;
import com.group1.career.service.CareerPlanService;
import com.group1.career.service.CareerService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Career API")
@RestController
@RequestMapping("/api/careers")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;
    private final CareerPlanService careerPlanService;
    private final AdminAuthService adminAuthService;

    // ─────────────────────────────────────────────
    // F28c: AI personalised career plan
    // ─────────────────────────────────────────────

    @Operation(summary = "Generate (or regenerate) AI career plan for current user")
    @PostMapping("/plan/generate")
    public Result<UserCareerPlan> generatePlan(@RequestBody(required = false) GeneratePlanRequest req) {
        Long userId = SecurityUtil.requireCurrentUserId();
        String targetRole = req != null ? req.getTargetRole() : null;
        UserCareerPlan plan = careerPlanService.generate(userId, targetRole);
        return Result.success(plan);
    }

    @Operation(summary = "Get the current AI career plan for current user")
    @GetMapping("/plan/current")
    public Result<UserCareerPlan> getCurrentPlan() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerPlanService.getCurrent(userId).orElse(null));
    }

    @Operation(summary = "Get all career paths")
    @GetMapping("/paths")
    public Result<List<CareerPath>> getAllPaths() {
        List<CareerPath> paths = careerService.getAllPaths();
        return Result.success(paths);
    }

    @Operation(summary = "Get career path by ID")
    @GetMapping("/paths/{pathId}")
    public Result<CareerPath> getPath(@PathVariable Integer pathId) {
        CareerPath path = careerService.getPathById(pathId);
        return Result.success(path);
    }

    @Operation(summary = "Get nodes for a career path")
    @GetMapping("/paths/{pathId}/nodes")
    public Result<List<CareerNode>> getPathNodes(@PathVariable Integer pathId) {
        List<CareerNode> nodes = careerService.getPathNodes(pathId);
        return Result.success(nodes);
    }

    @Operation(summary = "Get current user's career progress")
    @GetMapping({"/progress", "/progress/{ignoredUserId}"})
    public Result<List<UserCareerProgress>> getUserProgress(
            @PathVariable(required = false) Long ignoredUserId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        List<UserCareerProgress> progress = careerService.getUserProgress(userId);
        return Result.success(progress);
    }

    @Operation(summary = "Unlock a node for current user")
    @PostMapping("/progress/unlock")
    public Result<String> unlockNode(@RequestBody UnlockNodeRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        careerService.unlockNode(userId, request.getNodeId());
        return Result.success("Node unlocked successfully");
    }

    @Operation(summary = "Complete a node for current user")
    @PostMapping("/progress/complete")
    public Result<String> completeNode(@RequestBody CompleteNodeRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        careerService.completeNode(userId, request.getNodeId());
        return Result.success("Node completed successfully");
    }

    @Operation(summary = "Get L1-L4 structured timeline state for current user")
    @GetMapping("/timeline")
    public Result<TimelineStateResponseDto> getTimelineState(
            @RequestParam Integer pathId) {

        Long userId = SecurityUtil.requireCurrentUserId();
        List<CareerNode> allNodes = careerService.getPathNodes(pathId);
        List<UserCareerProgress> userProgress = careerService.getUserProgress(userId);

        Map<Integer, List<TimelineNodeDto>> groupedNodes = allNodes.stream()
                .map(node -> {
                    String status = userProgress.stream()
                            .filter(p -> p.getNodeId().equals(node.getNodeId()))
                            .map(UserCareerProgress::getStatus)
                            .findFirst()
                            .orElse("LOCKED");
                    return new TimelineNodeDto(node.getNodeId(), node.getName(), node.getLevel(), status);
                })
                .collect(Collectors.groupingBy(TimelineNodeDto::getLevel));

        TimelineStateResponseDto response = new TimelineStateResponseDto();
        response.setL1Nodes(groupedNodes.getOrDefault(1, Collections.emptyList()));
        response.setL2Nodes(groupedNodes.getOrDefault(2, Collections.emptyList()));
        response.setL3Nodes(groupedNodes.getOrDefault(3, Collections.emptyList()));
        response.setL4Nodes(groupedNodes.getOrDefault(4, Collections.emptyList()));

        return Result.success(response);
    }

    @Operation(summary = "Initialize default career paths (admin only)")
    @PostMapping("/initialize")
    public Result<String> initializePaths() {
        Long userId = SecurityUtil.requireCurrentUserId();
        adminAuthService.requireAdmin(userId);
        careerService.initializeDefaultPaths();
        return Result.success("Career paths initialized successfully");
    }

    // ================= DTO Classes =================

    @Data
    public static class GeneratePlanRequest {
        private String targetRole;
    }

    @Data
    public static class UnlockNodeRequest {
        /**
         * Kept only for backwards-compatible JSON deserialization. Ownership
         * always comes from the validated JWT and this value is never trusted.
         */
        @Deprecated
        private Long userId;
        private Long nodeId;
    }

    @Data
    public static class CompleteNodeRequest {
        /**
         * Kept only for backwards-compatible JSON deserialization. Ownership
         * always comes from the validated JWT and this value is never trusted.
         */
        @Deprecated
        private Long userId;
        private Long nodeId;
    }

    @Data
    public static class TimelineNodeDto {
        private Long nodeId;
        private String name;
        private Integer level;
        private String status; // "LOCKED", "IN_PROGRESS", "COMPLETED"

        public TimelineNodeDto(Long nodeId, String name, Integer level, String status) {
            this.nodeId = nodeId;
            this.name = name;
            this.level = level;
            this.status = status;
        }
    }

    @Data
    public static class TimelineStateResponseDto {
        private List<TimelineNodeDto> l1Nodes;
        private List<TimelineNodeDto> l2Nodes;
        private List<TimelineNodeDto> l3Nodes;
        private List<TimelineNodeDto> l4Nodes;
    }
}
