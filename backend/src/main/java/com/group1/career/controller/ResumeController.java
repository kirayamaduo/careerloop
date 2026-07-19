package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.Resume;
import com.group1.career.service.FileService;
import com.group1.career.service.ResumeKeywordService;
import com.group1.career.service.ResumeService;
import com.group1.career.utils.SecurityUtil;
import com.group1.career.utils.UserObjectKeyPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Resume API")
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final FileService fileService;
    private final ResumeKeywordService resumeKeywordService;

    @Operation(summary = "Create Resume (uses authenticated user)")
    @PostMapping
    public Result<Resume> createResume(@RequestBody CreateResumeRequest request) {
        // Always trust the JWT-authenticated user, never the body's userId,
        // otherwise any client can attribute resumes to arbitrary users.
        Long uid = SecurityUtil.requireCurrentUserId();
        String fileKey = request.getFileUrl() == null
                ? null
                : UserObjectKeyPolicy.requireOwnedKey(uid, "resumes", request.getFileUrl());
        Resume resume = resumeService.createResume(
                uid,
                request.getTitle(),
                request.getTargetJob(),
                fileKey,
                request.getParsedContent()
        );
        resumeKeywordService.triggerExtraction(uid, resume.getResumeId(), false);
        return Result.success(resumeService.hydrateUrl(resume));
    }

    @Operation(summary = "Get all resumes for the authenticated user")
    @GetMapping("/me")
    public Result<List<Resume>> getMyResumes() {
        Long uid = SecurityUtil.requireCurrentUserId();
        return Result.success(resumeService.hydrateUrls(resumeService.getUserResumes(uid)));
    }

    @Operation(summary = "Get all resumes for a user (must be self)")
    @GetMapping("/user/{userId}")
    public Result<List<Resume>> getUserResumes(@PathVariable Long userId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        if (!uid.equals(userId)) {
            throw new BizException(com.group1.career.common.ErrorCode.FORBIDDEN);
        }
        return Result.success(resumeService.hydrateUrls(resumeService.getUserResumes(userId)));
    }

    @Operation(summary = "Get Resume by ID (owner only)")
    @GetMapping("/{resumeId}")
    public Result<Resume> getResume(@PathVariable Long resumeId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        return Result.success(resumeService.hydrateUrl(resumeService.assertOwnership(resumeId, uid)));
    }

    @Operation(summary = "Delete Resume (owner only)")
    @DeleteMapping("/{resumeId}")
    public Result<String> deleteResume(@PathVariable Long resumeId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        resumeService.assertOwnership(resumeId, uid);
        resumeService.deleteResume(resumeId);
        return Result.success("Resume deleted successfully");
    }

    @Operation(summary = "Update Resume Metadata (owner only)")
    @PutMapping("/{resumeId}")
    public Result<Resume> updateResume(@PathVariable Long resumeId, @RequestBody UpdateResumeRequest request) {
        Long uid = SecurityUtil.requireCurrentUserId();
        Resume resume = resumeService.assertOwnership(resumeId, uid);
        if (request.getTitle() != null) resume.setTitle(request.getTitle());
        if (request.getTargetJob() != null) resume.setTargetJob(request.getTargetJob());
        if (request.getFileUrl() != null) {
            resume.setFileUrl(UserObjectKeyPolicy.requireOwnedKey(
                    uid, "resumes", request.getFileUrl()));
        }
        if (request.getParsedContent() != null) resume.setParsedContent(request.getParsedContent());
        return Result.success(resumeService.hydrateUrl(resumeService.updateResume(resume)));
    }

    /**
     * Owner-only PDF proxy stream. Avoids exposing the OSS URL to the client and
     * sidesteps the WeChat mini-program domain whitelist requirement
     * (the client only needs to talk to our own backend host).
     */
    @Operation(summary = "Stream the resume PDF bytes (owner only)")
    @GetMapping("/{resumeId}/download")
    public ResponseEntity<byte[]> downloadResume(@PathVariable Long resumeId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        Resume r = resumeService.assertOwnership(resumeId, uid);
        if (r.getFileUrl() == null || r.getFileUrl().isBlank()) {
            throw new BizException("Resume has no file attached");
        }
        // Bucket is private; must use authenticated OSS SDK access via FileService.
        byte[] bytes = fileService.downloadBytes(r.getFileUrl());
        String filename = (r.getTitle() == null ? "resume" : r.getTitle()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(bytes);
    }

    // ================= DTO Classes =================

    @Data
    public static class CreateResumeRequest {
        private Long userId;
        private String title;
        private String targetJob;
        private String fileUrl;
        /** JSON 字符串，包含 education/projects/skills/rawContent */
        private String parsedContent;
    }

    @Data
    public static class UpdateResumeRequest {
        private String title;
        private String targetJob;
        private String fileUrl;
        private String parsedContent;
    }
}
