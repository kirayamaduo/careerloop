package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.Resume;
import com.group1.career.service.AiService;
import com.group1.career.service.FileService;
import com.group1.career.service.ResumeService;
import com.group1.career.service.UserProfileTagService;
import com.group1.career.utils.PdfTextExtractor;
import com.group1.career.utils.SecurityUtil;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * AI-driven resume generation:
 *   POST /api/resume-gen/from-template   — generate from structured form data
 *   POST /api/resume-gen/tailor          — re-write an existing resume against a JD
 * Both pipelines: AI -> HTML -> PDF (OpenHTMLtoPDF) -> OSS upload -> persist Resume row.
 */
@Slf4j
@Tag(name = "Resume Generation API", description = "AI-driven resume creation & tailoring")
@RestController
@RequestMapping("/api/resume-gen")
@RequiredArgsConstructor
public class ResumeGenController {

    private static final int MAX_RESUME_TITLE_LEN = 50;
    private static final int MAX_TARGET_JOB_LEN = 50;

    private final AiService aiService;
    private final ResumeService resumeService;
    private final FileService fileService;
    private final UserProfileTagService profileTagService;

    @Operation(summary = "Generate a brand-new resume from template form data")
    @PostMapping("/from-template")
    // 不加 @Transactional：同 tailor，AI 调用期间不应持有数据库事务
    public Result<Resume> fromTemplate(@RequestBody TemplateRequest req) {
        Long uid = SecurityUtil.requireCurrentUserId();
        if (req.getName() == null || req.getName().isBlank()) throw new BizException("name is required");

        String prompt = buildTemplatePrompt(req, profileTagService.renderForPrompt(uid));
        String html = callAiForHtml(prompt);

        String fileKey = htmlToPdfAndUpload(html, "resumes/" + uid + "/generated");
        String title = (req.getTargetRole() == null ? req.getName() : req.getName() + "_" + req.getTargetRole())
                .replaceAll("\\s+", "_");
        Resume saved = resumeService.createResume(uid, title, req.getTargetRole(),
                fileKey, null);
        return Result.success(resumeService.hydrateUrl(saved));
    }

    @Operation(summary = "Tailor an existing resume against a Job Description")
    @PostMapping("/tailor")
    // 不加 @Transactional：AI rewrite + PDF render 耗时 30-90s，持有事务会耗尽连接池
    // assertOwnership / createResume 各自在自己的短事务中完成
    public Result<TailorResponse> tailor(@RequestBody TailorRequest req) {
        Long uid = SecurityUtil.requireCurrentUserId();
        if (req.getResumeId() == null) throw new BizException("resumeId is required");

        long t0 = System.currentTimeMillis();
        // Source resume must belong to the caller
        Resume base = resumeService.assertOwnership(req.getResumeId(), uid);

        String resumeText = base.getParsedContent();
        if ((resumeText == null || resumeText.isBlank()) && base.getFileUrl() != null) {
            long ts = System.currentTimeMillis();
            byte[] pdfBytes = fileService.downloadBytes(base.getFileUrl());
            resumeText = PdfTextExtractor.extractFromBytes(pdfBytes);
            log.info("[tailor] PDF download+extract took {} ms ({} bytes -> {} chars)",
                    System.currentTimeMillis() - ts, pdfBytes.length, resumeText == null ? 0 : resumeText.length());
        }
        if (resumeText == null || resumeText.isBlank()) {
            throw new BizException("Source resume has no readable content");
        }

        String prompt = buildTailorPrompt(resumeText, req.getJobDescription() == null ? "" : req.getJobDescription(),
                profileTagService.renderForPrompt(uid));
        long ts = System.currentTimeMillis();
        String html = callAiForHtml(prompt);
        log.info("[tailor] AI rewrite took {} ms ({} html chars)", System.currentTimeMillis() - ts, html.length());

        ts = System.currentTimeMillis();
        String fileKey = htmlToPdfAndUpload(html, "resumes/" + uid + "/tailored");
        log.info("[tailor] PDF render+OSS upload took {} ms", System.currentTimeMillis() - ts);

        String title = truncateField((base.getTitle() == null ? "Resume" : base.getTitle()) + "_tailored",
                MAX_RESUME_TITLE_LEN);
        Resume saved = resumeService.createResume(uid, title,
                summarizeTargetJob(req.getJobDescription()),
                fileKey, null);
        log.info("[tailor] DONE total {} ms, resumeId={}", System.currentTimeMillis() - t0, saved.getResumeId());
        TailorResponse response = new TailorResponse();
        response.setResume(resumeService.hydrateUrl(saved));
        response.setChangeItems(extractTailorChangeItems(html));
        response.setChangeSummary(response.getChangeItems().isEmpty()
                ? "已根据目标 JD 重新组织简历重点，并生成一份新的定制 PDF。"
                : "已根据目标 JD 完成岗位定制，重点改动如下。");
        return Result.success(response);
    }

    // ========================= Internals =========================

    private String buildTemplatePrompt(TemplateRequest r, String profileContext) {
        return "你是资深中文简历顾问。请生成一页中文求职简历 HTML，只输出 body 内容，使用 <h1>, <h2>, <ul>, <li>, <p>, <strong>。 " +
               "请用 STAR 方法扩展经历，优化表达，但不要编造用户没有提供的项目、成果、奖项或数据。 " +
               "可以参考结构化用户画像中的技能、成长方向、背景和目标。\n\n" +
               safe(profileContext) + "\n\n" +
               "请只返回 HTML，不要 Markdown 代码块。简历末尾加入一个小节 <h2>本次生成重点</h2>，用 2-3 条说明本次如何组织材料。\n\n" +
               "姓名: " + safe(r.getName()) + "\n" +
               "电话: " + safe(r.getPhone()) + "\n" +
               "邮箱: " + safe(r.getEmail()) + "\n" +
               "目标岗位: " + safe(r.getTargetRole()) + "\n" +
               "意向城市: " + safe(r.getCity()) + "\n" +
               "学校: " + safe(r.getUniversity()) + "\n" +
               "专业: " + safe(r.getMajor()) + "\n" +
               "学历: " + safe(r.getDegree()) + "\n" +
               "毕业年份: " + safe(r.getGraduationYear()) + "\n" +
               "技能: " + safe(r.getSkills()) + "\n" +
               "经历:\n" + safe(r.getExperience());
    }

    private String buildTailorPrompt(String resumeText, String jd, String profileContext) {
        return "你是资深中文简历定制顾问。请根据目标 JD 对下面的简历做明显、可感知的岗位定制，而不是轻微润色。 " +
               "必须完成：1) 按 JD 相关性重排模块；2) 强化岗位关键词；3) 重写项目/经历 bullet，让能力证据更清晰；" +
               "4) 突出最匹配的技能和项目；5) 保留事实边界，不编造项目、数据、奖项或经历。 " +
               "输出一页专业中文 HTML，只使用 <h1>, <h2>, <ul>, <li>, <p>, <strong>，只返回 body 内容。 " +
               "请在简历末尾加入 <h2>本次定制说明</h2>，列出 3-5 条本次具体改动和原因，让用户感知修改价值。\n\n" +
               safe(profileContext) + "\n\n" +
               "=== 目标 JD ===\n" + jd + "\n\n" +
               "=== 原始简历 ===\n" + resumeText;
    }

    private String callAiForHtml(String prompt) {
        String reply = aiService.chat(prompt);
        if (reply == null || reply.isBlank()) {
            throw new BizException("AI returned empty response");
        }
        // Strip ``` fences if model added them despite instructions
        String trimmed = reply.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
            int closingFence = trimmed.lastIndexOf("```");
            if (closingFence > 0) trimmed = trimmed.substring(0, closingFence);
        }
        return trimmed;
    }

    /** WQY MicroHei supports both Latin and CJK; installed via Dockerfile apt step. */
    private static final String WQY_FONT_PATH = "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc";
    private static final String WQY_FONT_FAMILY = "WQY";

    private String htmlToPdfAndUpload(String htmlBody, String folder) {
        // Use WQY as primary font so Chinese characters render correctly;
        // fall back to Helvetica for environments without the font (local dev).
        String fontFamily = new java.io.File(WQY_FONT_PATH).exists()
                ? "'" + WQY_FONT_FAMILY + "', 'Helvetica', sans-serif"
                : "'Helvetica', sans-serif";

        String wrapped = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head>" +
                "<meta charset=\"UTF-8\"/><style>" +
                "body{font-family:" + fontFamily + ";color:#222;line-height:1.45;font-size:11pt;margin:36pt}" +
                "h1{font-size:20pt;margin:0 0 6pt;color:#1e3a8a}" +
                "h2{font-size:13pt;margin:14pt 0 4pt;color:#1e40af;border-bottom:1pt solid #1e40af;padding-bottom:2pt}" +
                "ul{margin:4pt 0 4pt 16pt;padding:0}li{margin:2pt 0}" +
                "p{margin:4pt 0}strong{color:#0f172a}" +
                "</style></head><body>" + sanitizeForXhtml(htmlBody) + "</body></html>";

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Register CJK font if available (installed in production Docker image)
            java.io.File wqyFont = new java.io.File(WQY_FONT_PATH);
            if (wqyFont.exists()) {
                builder.useFont(wqyFont, WQY_FONT_FAMILY);
            }
            builder.withHtmlContent(wrapped, null);
            builder.toStream(bos);
            builder.run();
            byte[] pdfBytes = bos.toByteArray();
            String filename = "ai-resume-" + System.currentTimeMillis() + ".pdf";
            return fileService.uploadBytes(pdfBytes, filename, folder);
        } catch (Exception e) {
            log.error("PDF render/upload failed", e);
            throw new BizException("Resume PDF generation failed");
        }
    }

    private String sanitizeForXhtml(String html) {
        // Self-close common void elements that LLMs sometimes leave open
        return html
                .replaceAll("(?i)<br\\s*>", "<br/>")
                .replaceAll("(?i)<hr\\s*>", "<hr/>");
    }

    private String stripTags(String html) {
        return html == null ? "" : html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    List<String> extractTailorChangeItems(String html) {
        List<String> out = new ArrayList<>();
        if (html == null || html.isBlank()) return out;

        int marker = html.indexOf("本次定制说明");
        if (marker < 0) return out;
        String section = html.substring(marker);
        int nextH2 = section.indexOf("<h2", 8);
        if (nextH2 > 0) section = section.substring(0, nextH2);

        java.util.regex.Matcher liMatcher = java.util.regex.Pattern
                .compile("(?is)<li[^>]*>(.*?)</li>")
                .matcher(section);
        while (liMatcher.find() && out.size() < 5) {
            String item = stripTags(liMatcher.group(1));
            if (!item.isBlank()) out.add(item);
        }

        if (out.isEmpty()) {
            java.util.regex.Matcher pMatcher = java.util.regex.Pattern
                    .compile("(?is)<p[^>]*>(.*?)</p>")
                    .matcher(section);
            while (pMatcher.find() && out.size() < 5) {
                String item = stripTags(pMatcher.group(1));
                if (!item.isBlank() && !item.contains("本次定制说明")) out.add(item);
            }
        }

        return out;
    }

    private String safe(Object v) { return v == null ? "" : v.toString(); }

    /** DB column `target_job` is VARCHAR(50) — never persist the full JD blob. */
    private String summarizeTargetJob(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) return null;
        for (String line : jobDescription.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.matches("^[一二三四五六七八九十]+[、.．].*")) {
                int sep = Math.max(trimmed.indexOf('：'), trimmed.indexOf(':'));
                if (sep > 0 && sep < trimmed.length() - 1) {
                    trimmed = trimmed.substring(sep + 1).trim();
                } else {
                    continue;
                }
            }
            if (!trimmed.isEmpty()) {
                return truncateField(trimmed, MAX_TARGET_JOB_LEN);
            }
        }
        return truncateField(jobDescription, MAX_TARGET_JOB_LEN);
    }

    private static String truncateField(String value, int maxLen) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    // ========================= DTOs =========================

    @Data
    public static class TemplateRequest {
        private Long userId;
        private String name;
        private String phone;
        private String email;
        private String targetRole;
        private String city;
        private String university;
        private String major;
        private String degree;
        private String graduationYear;
        private String skills;
        private String experience;
    }

    @Data
    public static class TailorRequest {
        private Long userId;
        private Long resumeId;
        private String jobDescription;
    }

    @Data
    public static class TailorResponse {
        private Resume resume;
        private List<String> changeItems;
        private String changeSummary;
    }
}
