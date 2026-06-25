package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.model.dto.AgentBundleDto;
import com.group1.career.model.dto.AgentUserProfileDto;
import com.group1.career.model.dto.ProfileInputsRequest;
import com.group1.career.model.dto.CareerAgentPlanDto;
import com.group1.career.model.dto.CareerAgentRiskDto;
import com.group1.career.model.dto.CareerAgentTodayDto;
import com.group1.career.model.entity.AgentEvent;
import com.group1.career.model.entity.AgentState;
import com.group1.career.model.entity.AgentTask;
import com.group1.career.service.AgentEventService;
import com.group1.career.service.AgentProfileService;
import com.group1.career.service.AgentStateService;
import com.group1.career.service.CareerAgentService;
import com.group1.career.service.TaskDecomposer;
import com.group1.career.service.UserProfileTagService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Career Agent API", description = "Rule-driven proactive career agent")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class CareerAgentController {

    private final CareerAgentService careerAgentService;
    private final AgentProfileService agentProfileService;
    private final AgentEventService agentEventService;
    private final AgentStateService agentStateService;
    private final TaskDecomposer taskDecomposer;
    private final UserProfileTagService tagService;

    @Operation(summary = "Get unified career profile (personalization level + completeness + readiness)")
    @GetMapping("/profile")
    public Result<AgentUserProfileDto> profile() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(agentProfileService.getProfile(userId));
    }

    @Operation(summary = "Force-rebuild the unified career profile from all data sources")
    @PostMapping("/profile/refresh")
    public Result<AgentUserProfileDto> profileRefresh() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(agentProfileService.refresh(userId));
    }

    @Operation(summary = "Save user-supplied profile inputs (city, industry, timeline, etc.) and rebuild profile")
    @PostMapping("/profile/inputs")
    public Result<AgentUserProfileDto> profileInputs(@RequestBody ProfileInputsRequest req) {
        Long userId = SecurityUtil.requireCurrentUserId();
        AgentUserProfileDto profile = agentProfileService.saveInputs(userId, req);
        tagService.refreshFromSignals(userId);
        return Result.success(profile);
    }

    @Operation(summary = "Get all agent data in one request (today + tasks + risk + plan + profile)")
    @GetMapping("/bundle")
    public Result<AgentBundleDto> bundle() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.getBundle(userId));
    }

    @Operation(summary = "Get today's rule-driven career agent recommendation")
    @GetMapping("/today")
    public Result<CareerAgentTodayDto> today() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.getToday(userId));
    }

    @Operation(summary = "Get current risk watch from the rule-driven career agent")
    @GetMapping("/risk-watch")
    public Result<CareerAgentRiskDto> riskWatch() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.getRiskWatch(userId));
    }

    @Operation(summary = "Get long-term career plan summary for the agent")
    @GetMapping("/plan-summary")
    public Result<CareerAgentPlanDto> planSummary() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.getPlanSummary(userId));
    }

    @Operation(summary = "Ensure the current user has a long-term career plan")
    @PostMapping("/plan/ensure")
    public Result<CareerAgentPlanDto> ensurePlan() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.ensurePlan(userId));
    }

    @Operation(summary = "Get today's generated agent tasks")
    @GetMapping("/tasks/today")
    public Result<List<AgentTask>> todayTasks() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.getTodayTasks(userId));
    }

    @Operation(summary = "Get all open agent tasks")
    @GetMapping("/tasks/open")
    public Result<List<AgentTask>> openTasks() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.getOpenTasks(userId));
    }

    @Operation(summary = "Mark an agent task as done")
    @PostMapping("/tasks/{taskId}/complete")
    public Result<AgentTask> completeTask(@PathVariable Long taskId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.completeTask(userId, taskId));
    }

    @Operation(summary = "Dismiss an agent task")
    @PostMapping("/tasks/{taskId}/dismiss")
    public Result<AgentTask> dismissTask(@PathVariable Long taskId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(careerAgentService.dismissTask(userId, taskId));
    }

    @Operation(summary = "Get recent agent events (task completions, risk changes, etc.)")
    @GetMapping("/events")
    public Result<List<AgentEvent>> events(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(agentEventService.getRecentEvents(userId, limit));
    }

    @Operation(summary = "Get the current persisted agent state (stage, rates, last active)")
    @GetMapping("/state")
    public Result<AgentState> state() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(agentStateService.getOrCreate(userId));
    }

    @Operation(summary = "Get the latest weekly review event for the current user")
    @GetMapping("/weekly-review/latest")
    public Result<AgentEvent> weeklyReviewLatest() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(agentStateService.getLatestWeeklyReview(userId).orElse(null));
    }

    @Operation(summary = "Decompose a task into 2-4 concrete sub-tasks (idempotent)")
    @PostMapping("/tasks/{taskId}/decompose")
    public Result<List<AgentTask>> decomposeTask(@PathVariable Long taskId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(taskDecomposer.decompose(userId, taskId));
    }

    @Operation(summary = "Get sub-tasks of a decomposed task")
    @GetMapping("/tasks/{taskId}/subtasks")
    public Result<List<AgentTask>> subtasks(@PathVariable Long taskId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        AgentTask parent = careerAgentService.getOpenTasks(userId).stream()
                .filter(t -> t.getTaskId().equals(taskId)).findFirst().orElse(null);
        if (parent == null) return Result.success(List.of());
        return Result.success(taskDecomposer.decompose(userId, taskId));
    }
}
