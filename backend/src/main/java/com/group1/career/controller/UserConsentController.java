package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.model.entity.UserConsent;
import com.group1.career.repository.UserConsentRepository;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * F2: Records user consent server-side for audit / WeChat review purposes.
 *
 * <p>The frontend calls POST /api/consents after authentication. Anonymous
 * acceptance is kept locally until a real account is established, because an
 * unauthenticated database row cannot be reliably associated with a user.</p>
 */
@Tag(name = "Consent API", description = "F2: Record and verify user agreement to privacy policy and terms")
@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class UserConsentController {

    /** Must match the AGREEMENT_VERSION constant in the frontend consent page. */
    public static final String CURRENT_VERSION = "1.1";

    private final UserConsentRepository consentRepository;

    @Operation(summary = "Record that the current user has agreed to the specified agreement version")
    @PostMapping
    public Result<String> recordConsent(@RequestBody ConsentRequest req,
                                        HttpServletRequest httpRequest) {
        Long uid = SecurityUtil.currentUserId();
        if (uid == null) {
            return Result.error(401, "Not authenticated");
        }

        String version = req.getAgreementVersion();
        if (!CURRENT_VERSION.equals(version)) {
            return Result.error(400, "Unsupported agreement version");
        }

        String platform = req.getPlatform();
        if (!"miniprogram".equals(platform) && !"h5".equals(platform) && !"app".equals(platform)) {
            return Result.error(400, "Unsupported platform");
        }

        if (consentRepository.existsByUserIdAndAgreementVersion(uid, version)) {
            return Result.success("already_recorded");
        }

        String ip = extractIp(httpRequest);

        consentRepository.save(UserConsent.builder()
                .userId(uid)
                .agreementVersion(version)
                .agreedAt(LocalDateTime.now())
                .clientIp(ip)
                .platform(platform)
                .userAgent(truncate(req.getUserAgent(), 512))
                .build());

        return Result.success("recorded");
    }

    @Operation(summary = "Check whether the current user has agreed to the current agreement version")
    @GetMapping("/me/status")
    public Result<ConsentStatusDto> getStatus() {
        Long uid = SecurityUtil.currentUserId();
        if (uid == null) return Result.error(401, "Not authenticated");

        boolean agreed = consentRepository.existsByUserIdAndAgreementVersion(uid, CURRENT_VERSION);
        ConsentStatusDto dto = new ConsentStatusDto();
        dto.setAgreed(agreed);
        dto.setCurrentVersion(CURRENT_VERSION);
        return Result.success(dto);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(req.getRemoteAddr(), 64);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────

    @Data
    public static class ConsentRequest {
        private String agreementVersion;
        private String platform;
        private String userAgent;
    }

    @Data
    public static class ConsentStatusDto {
        private boolean agreed;
        private String currentVersion;
    }
}
