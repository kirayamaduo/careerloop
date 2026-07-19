package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.service.BodyLanguageService;
import com.group1.career.service.InterviewService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Body-language frame ingest endpoint. The mini-program voice room sends a
 * base64 jpeg every ~2s while the candidate is speaking. We delegate to
 * {@link BodyLanguageService} which forwards to the Python sidecar and
 * keeps the score until the interview ends.
 */
@Tag(name = "Body Language API", description = "Interview body-language scoring (Sprint C-1)")
@RestController
@RequestMapping("/api/body-language")
@RequiredArgsConstructor
public class BodyLanguageController {

    private final InterviewService interviewService;
    private final BodyLanguageService bodyLanguageService;

    @Operation(summary = "Submit one frame for body-language scoring (best-effort, ownership-checked)")
    @PostMapping("/frame")
    public Result<Void> submitFrame(@RequestBody FrameRequest req) {
        Long uid = SecurityUtil.requireCurrentUserId();
        if (req.getInterviewId() == null) {
            return Result.error(400, "interviewId is required");
        }
        if (req.getFrameBase64() == null || req.getFrameBase64().isBlank()) {
            return Result.error(400, "frameBase64 is required");
        }
        if (req.getFrameBase64().length() > BodyLanguageService.MAX_BASE64_CHARS) {
            return Result.error(413, "Frame payload is too large");
        }
        // Ownership keeps a malicious client from injecting frames into
        // someone else's interview to skew their report.
        interviewService.assertOwnership(req.getInterviewId(), uid);
        BodyLanguageService.SubmissionResult outcome =
                bodyLanguageService.recordFrame(uid, req.getInterviewId(), req.getFrameBase64());
        if (outcome == BodyLanguageService.SubmissionResult.RATE_LIMITED
                || outcome == BodyLanguageService.SubmissionResult.BUSY) {
            return Result.error(429, "Frame sampling is too frequent");
        }
        if (outcome == BodyLanguageService.SubmissionResult.TOO_LARGE) {
            return Result.error(413, "Frame payload is too large");
        }
        if (outcome == BodyLanguageService.SubmissionResult.INVALID) {
            return Result.error(400, "Invalid frame payload");
        }
        // DISABLED / NO_VALID_SIGNAL / SIDECAR_FAILED are best-effort drops:
        // the interview continues and no fabricated score enters the report.
        return Result.success();
    }

    @Data
    public static class FrameRequest {
        private Long interviewId;
        /** base64-encoded JPEG/PNG. */
        private String frameBase64;
    }
}
