package com.group1.career.controller;

import com.group1.career.aspect.RedactWebLog;
import com.group1.career.common.Result;
import com.group1.career.model.dto.CareerBridgeDtos.ConsumeLinkCodeRequest;
import com.group1.career.model.dto.CareerBridgeDtos.ConsumeLinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionRequest;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionResponse;
import com.group1.career.model.dto.CareerBridgeDtos.StudentPassportResponse;
import com.group1.career.service.CareerBridgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/career-platform/v1")
@RequiredArgsConstructor
public class CareerPlatformBridgeController {

    private final CareerBridgeService careerBridgeService;

    @RedactWebLog
    @PostMapping("/link-code/consume")
    public Result<ConsumeLinkCodeResponse> consumeLinkCode(
            @Valid @RequestBody ConsumeLinkCodeRequest request) {
        return Result.success(careerBridgeService.consumeLinkCode(request.getCode()));
    }

    @GetMapping("/students/{studentId}/passport")
    public Result<StudentPassportResponse> getStudentPassport(@PathVariable Long studentId) {
        return Result.success(careerBridgeService.getStudentPassport(studentId));
    }

    @PostMapping("/students/{studentId}/interventions")
    public Result<InterventionResponse> createIntervention(
            @PathVariable Long studentId,
            @Valid @RequestBody InterventionRequest request) {
        return Result.success(careerBridgeService.createIntervention(studentId, request));
    }
}
