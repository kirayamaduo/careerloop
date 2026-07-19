package com.group1.career.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.model.dto.HomeConsultationFeedDto;
import com.group1.career.model.entity.HomeArticle;
import com.group1.career.model.entity.HomeConsultation;
import com.group1.career.model.entity.Interview;
import com.group1.career.repository.InterviewRepository;
import com.group1.career.service.AiService;
import com.group1.career.service.HomeFieldTipsService;
import com.group1.career.service.UserFactService;
import com.group1.career.service.UserProfileSnapshotService;
import com.group1.career.service.UserProfileTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeFieldTipsServiceImpl implements HomeFieldTipsService {

    private static final String CACHE_PREFIX = "career:home:fieldtips:v3:";
    private static final String AI_MODEL = "qwen-turbo";

    private static final Set<String> ALLOWED_IN_APP_LINKS = Set.of(
            "/pages/assistant/index",
            "/pages/map/index",
            "/pages/resume-ai/index",
            "/pages/interview/start",
            "/pages/assessment/index",
            "/pages/checkin/index"
    );

    private final AiService aiService;
    private final UserProfileSnapshotService profileSnapshotService;
    private final UserFactService userFactService;
    private final UserProfileTagService profileTagService;
    private final InterviewRepository interviewRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<HomeConsultationFeedDto> buildPublicConsultationFeed(
            long seed,
            int limit,
            List<HomeConsultation> consultationPool,
            List<HomeArticle> articlePool
    ) {
        if (limit <= 0) return List.of();
        return topUpFromDatabase(new ArrayList<>(), consultationPool, articlePool, seed, limit);
    }

    @Override
    public List<HomeConsultationFeedDto> buildPersonalizedConsultationFeed(
            long authenticatedUserId,
            long seed,
            int limit,
            List<HomeConsultation> consultationPool,
            List<HomeArticle> articlePool
    ) {
        if (limit <= 0) return List.of();
        if (authenticatedUserId <= 0) {
            throw new IllegalArgumentException("authenticatedUserId must be positive");
        }

        String cacheKey = CACHE_PREFIX + LocalDate.now() + ":" + authenticatedUserId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() { });
            } catch (Exception e) {
                log.debug("[home-tips] cache parse failed: {}", e.toString());
            }
        }

        List<HomeConsultationFeedDto> aiTips = generatePersonalisedTips(authenticatedUserId, limit);
        List<HomeConsultationFeedDto> merged = new ArrayList<>(aiTips == null ? List.of() : aiTips);
        if (merged.size() < limit) {
            merged = topUpFromDatabase(merged, consultationPool, articlePool, seed, limit);
        }
        if (!merged.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(merged), Duration.ofHours(20));
            } catch (Exception e) {
                log.debug("[home-tips] cache write failed: {}", e.toString());
            }
        }
        return merged;
    }

    private List<HomeConsultationFeedDto> generatePersonalisedTips(long userId, int limit) {
        String profile = profileSnapshotService.renderForPrompt(userId);
        String facts = userFactService.renderForPrompt(userId);
        String tags = profileTagService.renderForPrompt(userId);
        String portrait = String.join("\n",
                profile == null ? "" : profile,
                tags == null ? "" : tags).trim();
        boolean sparse = portrait.isBlank() && (facts == null || facts.isBlank());

        int completed30 = interviewRepository
                .findByUserIdAndStatusAndStartedAtBetween(
                        userId,
                        "COMPLETED",
                        LocalDateTime.now().minusDays(30),
                        LocalDateTime.now())
                .size();

        List<Interview> recent = interviewRepository.findByUserIdOrderByStartedAtDesc(userId);
        String lastRole = "（暂无）";
        if (!recent.isEmpty() && recent.get(0).getPositionName() != null) {
            lastRole = recent.get(0).getPositionName();
        }

        String system = sparse
                ? "你是资深职业顾问。用户画像信息很少，请给适合大学生/应届/转行求职者的具体、可执行建议。"
                : "你是资深职业顾问。必须严格根据下方用户画像与事实记忆给出个性化建议，禁止泛泛鸡汤。";

        String userPrompt = """
                请输出恰好 %d 条「今天就能做的一小步」职业行动建议。

                【用户画像】
                %s

                【用户事实记忆】
                %s

                【数据】近30天完成模拟面试次数：%d；最近一次面试岗位：%s

                硬性规则：
                1. 仅输出一个 JSON 数组，长度恰好 %d，不要 markdown 代码块，不要其它说明文字。
                2. 每个元素必须是对象：{"title":"不超过22个汉字","body":"1~3句具体动作，每句可落地，总长度不超过200字","link":"必须是下列路径之一"}
                3. link 只能是：/pages/assistant/index /pages/map/index /pages/resume-ai/index /pages/interview/start /pages/assessment/index /pages/checkin/index
                4. title 和 body 必须使用简体中文。
                5. 至少有一条建议要明确呼应画像或事实中的具体信息（学校、专业、目标岗、测评代码、简历关键词等）；若画像为空，则聚焦「本周可完成的求职准备动作」。

                """.formatted(limit,
                portrait.isBlank() ? "（暂无）" : portrait,
                facts == null || facts.isBlank() ? "（暂无）" : facts,
                completed30,
                lastRole,
                limit);

        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", userPrompt)
            );
            String raw = aiService.chat(messages, AI_MODEL);
            if (raw == null || raw.isBlank() || raw.contains("暂时繁忙")) {
                return null;
            }
            return parseAiArray(raw, userId, limit);
        } catch (Exception e) {
            log.warn("[home-tips] AI generation failed for user={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private List<HomeConsultationFeedDto> parseAiArray(String raw, long userId, int limit) throws Exception {
        String json = stripJsonFences(raw.trim());
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray() || root.isEmpty()) {
            return null;
        }
        int day = LocalDate.now().getDayOfYear();
        int n = Math.min(limit, root.size());
        List<HomeConsultationFeedDto> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            JsonNode o = root.get(i);
            if (o == null || !o.isObject()) return null;
            String title = o.path("title").asText("").trim();
            String body = o.path("body").asText("").trim();
            String link = normalizeLink(o.path("link").asText("").trim());
            if (title.isEmpty() || body.isEmpty()) return null;
            long syntheticId = -(870_000L + day * 20L + userId % 10_000L + i);
            out.add(HomeConsultationFeedDto.builder()
                    .id(syntheticId)
                    .title(title)
                    .body(body)
                    .author("AI · 今日行动")
                    .sourceUrl(link)
                    .imageUrl(null)
                    .build());
        }
        return out;
    }

    private String normalizeLink(String link) {
        if (link == null || link.isBlank()) return "/pages/assistant/index";
        String base = link.split("\\?")[0];
        if (ALLOWED_IN_APP_LINKS.contains(base)) return base;
        for (String allowed : ALLOWED_IN_APP_LINKS) {
            if (base.toLowerCase(Locale.ROOT).startsWith(allowed)) return allowed;
        }
        return "/pages/assistant/index";
    }

    private static String stripJsonFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private List<HomeConsultationFeedDto> topUpFromDatabase(
            List<HomeConsultationFeedDto> existing,
            List<HomeConsultation> consultationPool,
            List<HomeArticle> articles,
            long seed,
            int limit
    ) {
        List<HomeConsultationFeedDto> out = new ArrayList<>(existing == null ? List.of() : existing);
        if (out.size() >= limit) {
            return new ArrayList<>(out.subList(0, limit));
        }

        if (consultationPool != null && !consultationPool.isEmpty()) {
            int offset = (int) Math.floorMod(seed, consultationPool.size());
            int cap = Math.min(limit - out.size(), consultationPool.size());
            for (int i = 0; i < cap; i++) {
                out.add(fromConsultationEntity(consultationPool.get((offset + i) % consultationPool.size())));
            }
        }

        if (out.size() >= limit) {
            return new ArrayList<>(out.subList(0, limit));
        }

        if (articles == null || articles.isEmpty()) {
            return out;
        }

        Set<String> usedLinks = new HashSet<>();
        for (HomeConsultationFeedDto c : out) {
            if (c.getSourceUrl() != null && !c.getSourceUrl().isBlank()) usedLinks.add(c.getSourceUrl());
        }

        int articleOffset = (int) Math.floorMod(seed / 7, articles.size());
        for (int i = 0; i < articles.size() && out.size() < limit; i++) {
            HomeArticle a = articles.get((articleOffset + i) % articles.size());
            if (a.getSourceUrl() != null && usedLinks.contains(a.getSourceUrl())) continue;
            out.add(HomeConsultationFeedDto.builder()
                    .id(a.getId() == null ? -(1000L + i) : -a.getId())
                    .title(a.getTitle() == null || a.getTitle().isBlank() ? "精选导读" : a.getTitle())
                    .body(a.getSummary() == null || a.getSummary().isBlank()
                            ? "点击阅读全文，把其中一点落实到今天。"
                            : a.getSummary())
                    .author("精选文章")
                    .sourceUrl(a.getSourceUrl())
                    .imageUrl(a.getImageUrl())
                    .build());
            if (a.getSourceUrl() != null && !a.getSourceUrl().isBlank()) usedLinks.add(a.getSourceUrl());
        }
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    private HomeConsultationFeedDto fromConsultationEntity(HomeConsultation c) {
        return HomeConsultationFeedDto.builder()
                .id(c.getId())
                .title(c.getTitle())
                .body(c.getBodyMd())
                .author(c.getAuthor())
                .sourceUrl(c.getSourceUrl())
                .imageUrl(c.getImageUrl())
                .build();
    }
}
