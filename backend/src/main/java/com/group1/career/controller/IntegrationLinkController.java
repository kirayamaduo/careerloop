package com.group1.career.controller;

import com.group1.career.aspect.RedactWebLog;
import com.group1.career.common.Result;
import com.group1.career.model.dto.CareerBridgeDtos.LinkCodeResponse;
import com.group1.career.service.CareerBridgeService;
import com.group1.career.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationLinkController {

    private final CareerBridgeService careerBridgeService;

    @RedactWebLog
    @PostMapping("/link-code")
    public Result<LinkCodeResponse> issueLinkCode() {
        return Result.success(careerBridgeService.issueLinkCode(SecurityUtil.requireCurrentUserId()));
    }
}
