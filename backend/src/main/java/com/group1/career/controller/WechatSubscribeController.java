package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.model.entity.WxSubscribeQuota;
import com.group1.career.repository.WxSubscribeQuotaRepository;
import com.group1.career.service.WechatSubscribeService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * F10: Frontend-facing endpoints for WeChat subscribe message quota.
 */
@Tag(name = "WX Subscribe API", description = "WeChat subscribe message quota management (F10)")
@RestController
@RequestMapping("/api/wx-subscribe")
@RequiredArgsConstructor
public class WechatSubscribeController {

    private final WechatSubscribeService subscribeService;
    private final WxSubscribeQuotaRepository quotaRepository;

    /**
     * Returns only template IDs that are configured on the server and have a
     * complete backend send path. It deliberately exposes no app secret or
     * placeholder IDs.
     */
    @Operation(summary = "Get configured subscribe-message templates")
    @GetMapping("/templates")
    public Result<Map<String, String>> getConfiguredTemplates() {
        SecurityUtil.requireCurrentUserId();
        return Result.success(subscribeService.getConfiguredTemplates());
    }

    /**
     * Called by the mini-program immediately after {@code wx.requestSubscribeMessage} resolves.
     * The {@code results} map has templateId → "accept" | "reject" | "ban".
     */
    @Operation(summary = "Record subscribe grant results from wx.requestSubscribeMessage")
    @PostMapping("/grant")
    public Result<String> recordGrant(@RequestBody GrantRequest req) {
        Long userId = SecurityUtil.requireCurrentUserId();
        subscribeService.recordGrant(userId, req.getResults());
        return Result.success("ok");
    }

    /**
     * Returns the remaining quota per template for the current user.
     * The mini-program uses this to decide which templates to show in the subscribe prompt.
     */
    @Operation(summary = "Get remaining subscribe quota for current user")
    @GetMapping("/quota")
    public Result<List<WxSubscribeQuota>> getQuota() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return Result.success(quotaRepository.findByUserId(userId));
    }

    @Data
    public static class GrantRequest {
        private Map<String, String> results;
    }
}
