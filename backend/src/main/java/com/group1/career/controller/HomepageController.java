package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.model.dto.HomeConsultationFeedDto;
import com.group1.career.model.entity.CareerPath;
import com.group1.career.model.entity.HomeArticle;
import com.group1.career.model.entity.HomeConsultation;
import com.group1.career.model.entity.HomeVideo;
import com.group1.career.repository.HomeArticleRepository;
import com.group1.career.repository.HomeConsultationRepository;
import com.group1.career.repository.HomeVideoRepository;
import com.group1.career.repository.InterviewRepository;
import com.group1.career.repository.UserRepository;
import com.group1.career.service.CareerService;
import com.group1.career.service.HomeContentRefreshJob;
import com.group1.career.service.HomeFieldTipsService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Aggregated home feed: B站 videos (rotated daily), career articles,
 * career consultations, and a small set of career-path "spotlight" cards.
 *
 * <p>Rotation strategy: videos are sampled with {@code MySQL RAND(seed)}
 * where seed = {@code dayOfYear * 10000 + (userId mod 10000)}. This gives
 * each user a stable batch within a day and a fresh slice every morning,
 * even before the daily cron runs.</p>
 */
@Slf4j
@Tag(name = "Homepage API", description = "Aggregated data feed for the homepage")
@RestController
@RequestMapping("/api/homepage")
@RequiredArgsConstructor
public class HomepageController {

    private static final int VIDEO_LIMIT = 8;
    private static final int ARTICLE_LIMIT = 6;
    private static final int CONSULTATION_LIMIT = 3;
    private static final int CAREER_CARD_LIMIT = 4;
    private static final String USER_AGENT = "Mozilla/5.0 CareerLoopBot/1.0";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final int REFRESH_RATE_LIMIT = 3;
    private static final long REFRESH_WINDOW_MINUTES = 5L;

    private final CareerService careerService;
    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final HomeVideoRepository homeVideoRepository;
    private final HomeArticleRepository homeArticleRepository;
    private final HomeConsultationRepository homeConsultationRepository;
    private final HomeContentRefreshJob homeContentRefreshJob;
    private final HomeFieldTipsService homeFieldTipsService;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "Proxy article cover (image_url or source og:image)")
    @GetMapping("/articles/{id}/cover")
    public ResponseEntity<byte[]> proxyArticleCover(@PathVariable long id) {
        Optional<HomeArticle> opt = homeArticleRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HomeArticle article = opt.get();

        String directImage = normalizeHttpUrl(article.getImageUrl());
        String sourceImage = extractOgImage(article.getSourceUrl());

        // Try configured image_url first; then source page og:image.
        ResponseEntity<byte[]> proxied = fetchImageResponse(directImage);
        if (proxied != null) return proxied;
        proxied = fetchImageResponse(sourceImage);
        if (proxied != null) return proxied;

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @Operation(summary = "Manually trigger a fresh Bilibili video pull (authenticated, rate-limited: 3×/5 min per user)")
    @PostMapping("/refresh")
    public Result<String> triggerRefresh() {
        Long userId = SecurityUtil.requireCurrentUserId();
        String key = "home:refresh:u:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, REFRESH_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count > REFRESH_RATE_LIMIT) {
            return Result.error(429, "刷新太频繁，请 5 分钟后再试");
        }
        CompletableFuture.runAsync(() -> {
            try {
                int written = homeContentRefreshJob.refresh();
                log.info("[home-refresh] manual trigger wrote {} new videos (user={})", written, userId);
            } catch (Exception e) {
                log.error("[home-refresh] manual trigger failed", e);
            }
        });
        return Result.success("内容刷新已触发");
    }

    @Operation(summary = "Get public homepage feed (never includes user-personalised content)")
    @GetMapping("/feed")
    public Result<HomepageFeedDto> getHomepageFeed() {
        return buildHomepageFeed(null, false);
    }

    @Operation(summary = "Get current user's personalised homepage feed")
    @GetMapping("/feed/personalized")
    public Result<HomepageFeedDto> getPersonalizedHomepageFeed() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return buildHomepageFeed(userId, true);
    }

    private Result<HomepageFeedDto> buildHomepageFeed(Long authenticatedUserId, boolean personalized) {

        // 1. Bilibili videos — rotated by day so the same user sees a stable
        //    batch within a day but fresh content the next morning.
        long seed = todaysSeed(authenticatedUserId);
        List<HomeVideo> videos;
        try {
            videos = homeVideoRepository.sampleByRand(seed, VIDEO_LIMIT);
        } catch (Exception e) {
            // RAND(seed) isn't supported on H2 (used in tests); fall back to
            // a deterministic top-by-score listing so the test path still works.
            log.debug("[homepage] RAND() sampling unavailable, falling back to sort: {}", e.getMessage());
            videos = homeVideoRepository.findAllByOrderBySortScoreDesc(PageRequest.of(0, VIDEO_LIMIT));
        }
        List<VideoCardDto> videoCards = videos.stream()
                .map(this::toVideoCard)
                .collect(Collectors.toList());

        // 2. Articles — pinned first, then newest, excludes hidden.
        List<ArticleDto> articles = homeArticleRepository
                .findAllByHiddenFalseOrderByPinnedDescPublishedAtDescIdDesc(PageRequest.of(0, ARTICLE_LIMIT))
                .stream()
                .map(this::toArticleDto)
                .collect(Collectors.toList());

        // 3. "From the Field" — AI-personalised tips for signed-in users (cached
        //    per day) with DB + article fallback; guests get rotation only.
        List<HomeConsultation> consultationPool = homeConsultationRepository
                .findAllByOrderByPublishedAtDescIdDesc(PageRequest.of(0, 24));
        List<HomeArticle> articlePool = homeArticleRepository
                .findAllByHiddenFalseOrderByPinnedDescPublishedAtDescIdDesc(PageRequest.of(0, 20));
        List<HomeConsultationFeedDto> consultations = personalized
                ? homeFieldTipsService.buildPersonalizedConsultationFeed(
                        authenticatedUserId, seed, CONSULTATION_LIMIT, consultationPool, articlePool)
                : homeFieldTipsService.buildPublicConsultationFeed(
                        seed, CONSULTATION_LIMIT, consultationPool, articlePool);

        // 4. Career path spotlight cards (kept for the Skill Map cross-link).
        List<CareerPath> paths = careerService.getAllPaths();
        List<CareerCardDto> careerCards = paths.stream()
                .sorted(Comparator.comparing(CareerPath::getPathId))
                .limit(CAREER_CARD_LIMIT)
                .map(p -> CareerCardDto.builder()
                        .pathId(p.getPathId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());

        // 5. Quick stats — live counts.
        QuickStatsDto stats = QuickStatsDto.builder()
                .totalCareerPaths(paths.size())
                .totalUsers((int) userRepository.count())
                .totalInterviews((int) interviewRepository.count())
                .build();

        HomepageFeedDto feed = HomepageFeedDto.builder()
                .videos(videoCards)
                .articles(articles)
                .consultations(consultations)
                .careerCards(careerCards)
                .stats(stats)
                .build();

        return Result.success(feed);
    }

    /**
     * Stable per-day, per-user random seed. {@code dayOfYear} guarantees the
     * batch rotates daily; mixing in {@code userId} keeps two users on the
     * same day from seeing the identical sample, which makes the home page
     * feel personalised even though we aren't running real recommendations.
     */
    private long todaysSeed(Long userId) {
        int day = LocalDate.now().getDayOfYear();
        long uid = userId == null ? 0L : Math.abs(userId % 10_000);
        return ((long) day) * 10_000L + uid;
    }

    private VideoCardDto toVideoCard(HomeVideo v) {
        return VideoCardDto.builder()
                .id(v.getId())
                .bvid(v.getBvid())
                .title(v.getTitle())
                .coverUrl(v.getCoverUrl())
                .upName(v.getUpName())
                .durationSec(v.getDurationSec())
                .viewCount(v.getViewCount())
                .keyword(v.getKeyword())
                // Frontend opens this URL in <web-view> (requires *.bilibili.com
                // on the WeChat MP business-domain whitelist).
                .url("https://www.bilibili.com/video/" + v.getBvid())
                .build();
    }

    private ArticleDto toArticleDto(HomeArticle a) {
        return ArticleDto.builder()
                .id(a.getId())
                .title(a.getTitle())
                .summary(a.getSummary())
                .imageUrl(a.getImageUrl())
                .url(a.getSourceUrl())
                .category(a.getCategory())
                .build();
    }

    private ResponseEntity<byte[]> fetchImageResponse(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() < 200 || res.statusCode() >= 300) return null;

            String ct = res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (!ct.startsWith("image/")) return null;
            MediaType mediaType = MediaType.parseMediaType(ct);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=21600")
                    .body(res.body());
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            log.debug("[homepage] proxy cover failed for {}: {}", url, e.toString());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String extractOgImage(String sourceUrl) {
        String source = normalizeHttpUrl(sourceUrl);
        if (source == null || source.isBlank()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(source))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) return null;
            String html = res.body();
            if (html == null || html.isBlank()) return null;

            Pattern p1 = Pattern.compile("(?is)<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>");
            Pattern p2 = Pattern.compile("(?is)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"'][^>]*>");
            Pattern p3 = Pattern.compile("(?is)<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>");
            Pattern p4 = Pattern.compile("(?is)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']twitter:image[\"'][^>]*>");

            for (Pattern p : List.of(p1, p2, p3, p4)) {
                Matcher m = p.matcher(html);
                if (m.find()) {
                    return normalizeHttpUrl(m.group(1));
                }
            }
        } catch (Exception e) {
            log.debug("[homepage] extract og:image failed for {}: {}", sourceUrl, e.toString());
        }
        return null;
    }

    private String normalizeHttpUrl(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("//")) return "https:" + s;
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        return null;
    }

    // ================= DTO Classes =================

    @Data @Builder @AllArgsConstructor
    public static class HomepageFeedDto {
        private List<VideoCardDto> videos;
        private List<ArticleDto> articles;
        private List<HomeConsultationFeedDto> consultations;
        private List<CareerCardDto> careerCards;
        private QuickStatsDto stats;
    }

    @Data @Builder @AllArgsConstructor
    public static class VideoCardDto {
        private Long id;
        private String bvid;
        private String title;
        private String coverUrl;
        private String upName;
        private Integer durationSec;
        private Long viewCount;
        private String keyword;
        private String url;
    }

    @Data @Builder @AllArgsConstructor
    public static class ArticleDto {
        private Long id;
        private String title;
        private String summary;
        private String imageUrl;
        private String url;
        private String category;
    }

    @Data @Builder @AllArgsConstructor
    public static class CareerCardDto {
        private Integer pathId;
        private String name;
        private String description;
    }

    @Data @Builder @AllArgsConstructor
    public static class QuickStatsDto {
        private int totalCareerPaths;
        private int totalUsers;
        private int totalInterviews;
    }
}
