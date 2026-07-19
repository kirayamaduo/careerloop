package com.group1.career.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.dto.ResumeKeywordDto;
import com.group1.career.model.entity.Resume;
import com.group1.career.model.entity.ResumeProfileKeyword;
import com.group1.career.model.entity.UserProfileTag;
import com.group1.career.repository.ResumeProfileKeywordRepository;
import com.group1.career.repository.ResumeRepository;
import com.group1.career.service.FileService;
import com.group1.career.service.ResumeKeywordService;
import com.group1.career.service.UserProfileTagService;
import com.group1.career.utils.PdfTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeKeywordServiceImpl implements ResumeKeywordService {

    private static final String STATUS_CATEGORY = "META";
    private static final String STATUS_LABEL = "__resume_keyword_status__";
    private static final Duration BUSY_TIMEOUT = Duration.ofMinutes(2);
    private static final Set<String> BUSY = Set.of(ResumeProfileKeyword.STATUS_PENDING, ResumeProfileKeyword.STATUS_PROCESSING);
    private static final Set<String> CONTACT_TOKENS = Set.of(
            "email", "mail", "phone", "tel", "mobile", "wechat", "weixin", "qq", "github", "linkedin"
    );
    private static final Set<String> NON_SKILL_TOKENS = Set.of(
            "本科", "课程", "核心课程", "项目经历", "专业排名前"
    );
    private static final Set<String> STOPWORDS = Set.of(
            "用户", "目标", "岗位", "状态", "未知", "暂无", "简历状态", "简历匹配",
            "公司", "负责", "参与", "完成", "熟悉", "掌握", "了解", "使用", "进行", "相关",
            "basic", "profile", "target", "role", "state", "status", "null", "none", "no", "yes",
            "company", "responsible", "use", "used", "using", "related"
    );
    private static final List<String> KNOWN_TERMS = List.of(
            "前端", "后端", "全栈", "算法", "数据", "产品", "运营", "设计", "测试", "开发", "工程师",
            "Java", "Python", "JavaScript", "TypeScript", "Vue", "React", "Spring", "SpringBoot",
            "Node", "Express", "UniApp", "微信小程序", "小程序", "HTML", "CSS", "Sass", "Less",
            "SQL", "MySQL", "PostgreSQL", "MongoDB", "Redis", "Docker", "Kubernetes", "Git", "Linux",
            "AI", "AIGC", "LLM", "NLP", "UI", "UX",
            "沟通", "表达", "项目", "实习", "面试", "校招", "求职", "匹配", "学习", "探索",
            "应届", "学生", "转行", "城市", "远程", "作品集", "英语", "技术", "管理", "研究",
            "本科", "硕士", "博士", "学士", "课程", "竞赛", "奖学金", "论文", "证书",
            "后端工程师", "前端工程师", "产品经理", "数据分析师", "算法工程师"
    );

    private final ResumeRepository resumeRepository;
    private final ResumeProfileKeywordRepository keywordRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ResumeKeywordService> selfProvider;
    private final ObjectProvider<UserProfileTagService> tagServiceProvider;
    @Qualifier("resumeKeywordTaskExecutor")
    private final TaskExecutor resumeKeywordTaskExecutor;

    @Override
    @Transactional(readOnly = true)
    public ResumeKeywordDto.Status getStatus(Long userId, Long resumeId) {
        Resume resume = assertOwnership(userId, resumeId);
        return buildStatus(resume, keywordRepository.findByResumeIdOrderByWeightDescUpdatedAtDesc(resumeId));
    }

    @Override
    @Transactional
    public ResumeKeywordDto.Status triggerExtraction(Long userId, Long resumeId, boolean force) {
        Resume resume = assertOwnership(userId, resumeId);
        List<ResumeProfileKeyword> existing = keywordRepository.findByResumeIdOrderByWeightDescUpdatedAtDesc(resumeId);
        ResumeKeywordDto.Status current = buildStatus(resume, existing);
        if (!force && (ResumeProfileKeyword.STATUS_READY.equals(current.getStatus())
                || ResumeProfileKeyword.STATUS_EMPTY.equals(current.getStatus())
                || ResumeProfileKeyword.STATUS_FAILED.equals(current.getStatus())
                || (BUSY.contains(current.getStatus()) && !isStale(existing)))) {
            return current;
        }
        markStatus(resume, ResumeProfileKeyword.STATUS_PENDING, null);
        Runnable task = () -> resumeKeywordTaskExecutor.execute(() -> {
            try {
                selfProvider.getObject().extractAsync(userId, resumeId, force);
                try {
                    tagServiceProvider.getObject().refreshFromSignals(userId);
                } catch (Exception e) {
                    log.warn("[resume-keywords] profile refresh failed after resume extraction user={} resume={}: {}",
                            userId, resumeId, e.toString());
                }
            } catch (Exception e) {
                log.warn("[resume-keywords] background task failed before status update resume={}: {}", resumeId, e.toString());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
        return buildStatus(resume, keywordRepository.findByResumeIdOrderByWeightDescUpdatedAtDesc(resumeId));
    }

    @Override
    @Transactional
    public void extractAsync(Long userId, Long resumeId, boolean force) {
        Resume resume = assertOwnership(userId, resumeId);
        List<ResumeProfileKeyword> existing = keywordRepository.findByResumeIdOrderByWeightDescUpdatedAtDesc(resumeId);
        ResumeKeywordDto.Status current = buildStatus(resume, existing);
        if (!force && (ResumeProfileKeyword.STATUS_READY.equals(current.getStatus())
                || ResumeProfileKeyword.STATUS_EMPTY.equals(current.getStatus()))) {
            return;
        }
        markStatus(resume, ResumeProfileKeyword.STATUS_PROCESSING, null);
        try {
            ensureResumeText(resume);
            List<KeywordSpec> keywords = extractKeywords(resume);
            clearResumeKeywordRows(resumeId);
            if (keywords.isEmpty()) {
                markStatus(resume, ResumeProfileKeyword.STATUS_EMPTY, "No readable resume text or useful keywords");
            } else {
                for (KeywordSpec spec : keywords) {
                    keywordRepository.save(ResumeProfileKeyword.builder()
                            .resumeId(resumeId)
                            .userId(userId)
                            .category(spec.category())
                            .label(spec.label())
                            .weight(spec.weight())
                            .source("RESUME")
                            .evidence("resume:" + resumeId + " " + spec.evidence())
                            .status(ResumeProfileKeyword.STATUS_READY)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[resume-keywords] extraction failed resume={}: {}", resumeId, e.toString());
            markStatus(resume, ResumeProfileKeyword.STATUS_FAILED, trim(e.getMessage(), 250));
        }
    }

    private Resume assertOwnership(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new BizException(ErrorCode.RESUME_NOT_FOUND));
        if (!Objects.equals(resume.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return resume;
    }

    private ResumeKeywordDto.Status buildStatus(Resume resume, List<ResumeProfileKeyword> rows) {
        List<ResumeProfileKeyword> keywords = rows.stream().filter(this::isKeywordRow).toList();
        Optional<ResumeProfileKeyword> marker = rows.stream().filter(row -> !isKeywordRow(row)).findFirst();
        String status = !keywords.isEmpty()
                ? ResumeProfileKeyword.STATUS_READY
                : marker.map(ResumeProfileKeyword::getStatus).orElse(ResumeProfileKeyword.STATUS_PENDING);
        String error = marker.map(ResumeProfileKeyword::getErrorMsg).orElse(null);
        return ResumeKeywordDto.Status.builder()
                .resumeId(resume.getResumeId())
                .status(status)
                .errorMsg(error)
                .keywords(keywords.stream().map(this::toDto).toList())
                .build();
    }

    private boolean isKeywordRow(ResumeProfileKeyword row) {
        return row != null && !STATUS_CATEGORY.equals(row.getCategory()) && !STATUS_LABEL.equals(row.getLabel());
    }

    private boolean isStale(List<ResumeProfileKeyword> rows) {
        Optional<ResumeProfileKeyword> marker = rows.stream().filter(row -> !isKeywordRow(row)).findFirst();
        if (marker.isEmpty() || !BUSY.contains(marker.get().getStatus())) return false;
        LocalDateTime updatedAt = marker.get().getUpdatedAt();
        return updatedAt != null && updatedAt.isBefore(LocalDateTime.now().minus(BUSY_TIMEOUT));
    }

    private void markStatus(Resume resume, String status, String errorMsg) {
        List<ResumeProfileKeyword> rows = keywordRepository.findByResumeIdOrderByWeightDescUpdatedAtDesc(resume.getResumeId());
        ResumeProfileKeyword marker = rows.stream()
                .filter(row -> !isKeywordRow(row))
                .findFirst()
                .orElse(null);
        List<ResumeProfileKeyword> keywordRows = rows.stream().filter(this::isKeywordRow).toList();
        if (!keywordRows.isEmpty()) {
            keywordRepository.deleteAll(keywordRows);
        }
        if (marker == null) {
            marker = ResumeProfileKeyword.builder()
                    .resumeId(resume.getResumeId())
                    .userId(resume.getUserId())
                    .category(STATUS_CATEGORY)
                    .label(STATUS_LABEL)
                    .weight(0)
                    .source("SYSTEM")
                    .evidence("resume:" + resume.getResumeId() + " extraction status")
                    .build();
        }
        marker.setStatus(status);
        marker.setErrorMsg(errorMsg);
        marker.setUserId(resume.getUserId());
        marker.setWeight(0);
        marker.setSource("SYSTEM");
        marker.setEvidence("resume:" + resume.getResumeId() + " extraction status");
        keywordRepository.save(marker);
        keywordRepository.flush();
    }

    private void clearResumeKeywordRows(Long resumeId) {
        List<ResumeProfileKeyword> rows = keywordRepository.findByResumeIdOrderByWeightDescUpdatedAtDesc(resumeId);
        if (!rows.isEmpty()) {
            keywordRepository.deleteAll(rows);
            keywordRepository.flush();
        }
    }

    private void ensureResumeText(Resume resume) {
        if (hasText(resume.getParsedContent())) return;
        if (!hasText(resume.getFileUrl())) return;
        byte[] pdfBytes = fileService.downloadBytes(resume.getFileUrl());
        String text = PdfTextExtractor.extractFromBytes(pdfBytes);
        if (!hasText(text)) return;
        try {
            resume.setParsedContent(objectMapper.writeValueAsString(Map.of("rawContent", text)));
            resumeRepository.save(resume);
        } catch (Exception e) {
            log.error("Failed to persist parsed resume text", e);
            throw new BizException("Failed to persist parsed resume text");
        }
    }

    private List<KeywordSpec> extractKeywords(Resume resume) {
        List<KeywordSpec> out = new ArrayList<>();
        addTokens(out, UserProfileTag.CATEGORY_GOAL, resume.getTargetJob(), 76, "target job");
        addTokens(out, UserProfileTag.CATEGORY_BACKGROUND, resume.getTitle(), 48, "title");
        if (resume.getUpdatedAt() != null) {
            add(out, UserProfileTag.CATEGORY_GROWTH, String.valueOf(resume.getUpdatedAt().getYear()), 54, "updated year");
        }
        if (hasText(resume.getParsedContent())) {
            try {
                JsonNode root = objectMapper.readTree(resume.getParsedContent());
                addJsonText(out, UserProfileTag.CATEGORY_SKILL, root.path("skills"), 82, "skills");
                addJsonText(out, UserProfileTag.CATEGORY_BACKGROUND, root.path("education"), 66, "education");
                addJsonText(out, UserProfileTag.CATEGORY_SKILL, root.path("projects"), 72, "projects");
                addJsonText(out, UserProfileTag.CATEGORY_GROWTH, root.path("experience"), 70, "experience");
                addJsonText(out, UserProfileTag.CATEGORY_GROWTH, root.path("work"), 70, "work");
                addJsonText(out, UserProfileTag.CATEGORY_SKILL, root.path("rawContent"), 62, "raw content");
            } catch (Exception ignored) {
                addTokens(out, UserProfileTag.CATEGORY_SKILL, resume.getParsedContent(), 54, "parsed content");
            }
        }
        return merge(out).stream().limit(24).toList();
    }

    private void addJsonText(List<KeywordSpec> out, String category, JsonNode node, int weight, String evidence) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            addTokens(out, category, node.asText(), weight, evidence);
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> addJsonText(out, category, child, weight, evidence));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> addJsonText(out, category, entry.getValue(), weight, evidence + ":" + entry.getKey()));
        }
    }

    private void addTokens(List<KeywordSpec> out, String category, String text, int weight, String evidence) {
        if (!hasText(text)) return;
        boolean includeTime = !UserProfileTag.CATEGORY_SKILL.equals(category);
        for (String token : tokenize(text, includeTime)) {
            add(out, category, token, token.equalsIgnoreCase(text.trim()) ? weight : Math.max(35, weight - 8), evidence);
        }
    }

    private void add(List<KeywordSpec> out, String category, String label, int weight, String evidence) {
        String clean = normalizeLabel(label);
        if (!isUsefulToken(clean)) return;
        if (UserProfileTag.CATEGORY_SKILL.equals(category) && (isTimeToken(clean) || NON_SKILL_TOKENS.contains(clean))) return;
        out.add(new KeywordSpec(category, clean, clamp(weight, 1, 100), evidence));
    }

    private List<KeywordSpec> merge(List<KeywordSpec> specs) {
        Map<String, KeywordSpec> merged = new LinkedHashMap<>();
        for (KeywordSpec spec : specs) {
            String key = spec.category() + "::" + spec.label().toLowerCase(Locale.ROOT);
            KeywordSpec current = merged.get(key);
            if (current == null || spec.weight() > current.weight()) {
                merged.put(key, spec);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(KeywordSpec::weight).reversed())
                .collect(Collectors.toList());
    }

    private List<String> tokenize(String text, boolean includeTime) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String normalized = normalizeLabel(text)
                .replaceAll("[，。；、,;|/\\\\()（）\\[\\]【】{}:：&]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (includeTime) addTimeTokens(text, out);
        for (String part : normalized.split(" ")) {
            String p = part.trim();
            if (!hasText(p)) continue;
            if (isContactLike(p)) continue;
            if (!includeTime && isTimeToken(p)) continue;
            for (String term : KNOWN_TERMS) {
                if (containsIgnoreCase(p, term)) out.add(term);
            }
            if (p.matches("[A-Za-z][A-Za-z0-9#.+-]{1,23}")) {
                out.add(p);
            } else if (p.matches("[A-Za-z0-9#.+-]+")) {
                for (String token : p.split("(?=[A-Z])|[-_]+")) {
                    if (isUsefulToken(token)) out.add(token);
                }
            } else {
                addMeaningfulChineseTokens(p, out);
            }
        }
        return out.stream().filter(this::isUsefulToken).toList();
    }

    private void addMeaningfulChineseTokens(String text, Set<String> out) {
        String zh = text.replaceAll("[^\\p{IsHan}A-Za-z0-9#.+-]", "");
        if (!hasText(zh)) return;
        if (zh.length() <= 6 && looksLikeCareerToken(zh)) out.add(zh);
    }

    private void addTimeTokens(String label, Set<String> out) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)((?:19|20)\\d{2})(?:[./年]?(0?[1-9]|1[0-2])月?)?")
                .matcher(label == null ? "" : label);
        while (matcher.find()) {
            String year = matcher.group(1);
            String month = matcher.group(2);
            out.add(month == null ? year : year + "." + month.replaceFirst("^0", ""));
        }
    }

    private boolean looksLikeCareerToken(String token) {
        if (!isUsefulToken(token)) return false;
        if (KNOWN_TERMS.stream().anyMatch(term -> containsIgnoreCase(token, term))) return true;
        return token.matches(".*(工程师|开发|算法|数据|产品|运营|设计|实习|项目|课程|竞赛|证书|本科|硕士|博士|学校|大学|学院|专业|面试|校招|转行|英语).*");
    }

    private boolean isUsefulToken(String token) {
        String clean = normalizeLabel(token);
        if (!hasText(clean)) return false;
        if (clean.length() < 2 && !clean.matches("AI|UI|UX")) return false;
        if (clean.length() > 24) return false;
        if (isContactLike(clean)) return false;
        if (clean.matches("(?i).*(email|mail|phone|mobile|wechat|weixin|github|linkedin).*")) return false;
        if (clean.matches("\\+?\\d{2,}[-\\d\\s]{4,}")) return false;
        if (clean.matches("\\d+(?:\\.\\d+)+") && !clean.matches("(?:19|20)\\d{2}(?:\\.\\d{1,2})?")) return false;
        if (clean.matches("\\d+") && !clean.matches("(19|20)\\d{2}")) return false;
        return !STOPWORDS.contains(clean.toLowerCase(Locale.ROOT));
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value
                .replace("：", ":")
                .replaceAll("\\s+", " ")
                .replaceAll("^[,.;:!?，。；：！？]+|[,.;:!?，。；：！？]+$", "")
                .trim();
    }

    private boolean isContactLike(String value) {
        if (!hasText(value)) return false;
        String clean = normalizeLabel(value);
        String lower = clean.toLowerCase(Locale.ROOT);
        if (CONTACT_TOKENS.contains(lower)) return true;
        if (lower.contains("@")) return true;
        if (lower.matches("https?://.*|www\\..*")) return true;
        if (lower.matches("\\+?86")) return true;
        return lower.matches("\\+?\\d[\\d\\s-]{6,}\\d");
    }

    private boolean isTimeToken(String value) {
        if (!hasText(value)) return false;
        String clean = normalizeLabel(value);
        return clean.matches("(?:19|20)\\d{2}(?:\\.\\d{1,2})?");
    }

    private boolean containsIgnoreCase(String text, String term) {
        return text != null && term != null && text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private ResumeKeywordDto toDto(ResumeProfileKeyword keyword) {
        return ResumeKeywordDto.builder()
                .category(keyword.getCategory())
                .label(keyword.getLabel())
                .weight(keyword.getWeight())
                .evidence(keyword.getEvidence())
                .build();
    }

    private record KeywordSpec(String category, String label, int weight, String evidence) {}
}
