package com.group1.career.controller;

import com.group1.career.aspect.RedactWebLog;
import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.dto.CareerBridgeDtos.LinkCodeResponse;
import com.group1.career.service.CareerBridgeService;
import com.group1.career.utils.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/integration")
public class IntegrationLinkController {

    private final CareerBridgeService careerBridgeService;
    private final String publicPassportUrl;

    public IntegrationLinkController(
            CareerBridgeService careerBridgeService,
            @Value("${career.bridge.public-url:}") String publicPassportUrl) {
        this.careerBridgeService = careerBridgeService;
        this.publicPassportUrl = publicPassportUrl;
    }

    @RedactWebLog
    @PostMapping("/link-code")
    public Result<LinkCodeResponse> issueLinkCode() {
        if (!isTrustedPassportUrl(publicPassportUrl)) {
            throw new BizException(503, "安全入口暂未配置");
        }
        return Result.success(careerBridgeService.issueLinkCode(SecurityUtil.requireCurrentUserId()));
    }

    static boolean isTrustedPassportUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || host.isBlank()
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                return false;
            }
            String normalizedHost = host.toLowerCase();
            if ("localhost".equals(normalizedHost)
                    || normalizedHost.endsWith(".local")
                    || !normalizedHost.contains(".")
                    || normalizedHost.contains(":")
                    || normalizedHost.matches("[0-9.]+")) {
                return false;
            }
            int port = uri.getPort();
            if (port == 0 || port > 65535) {
                return false;
            }
            String path = uri.getPath();
            return "/passport".equals(path) || "/passport/".equals(path);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
