package com.group1.career.service;

import com.group1.career.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window protection for unauthenticated authentication endpoints.
 *
 * <p>Redis keeps limits consistent across application replicas. If Redis is
 * temporarily unavailable, requests are still limited by a per-process
 * fallback instead of failing open. The IP bucket is always evaluated before
 * the subject bucket, which also bounds fallback-map growth when an attacker
 * rotates email addresses.</p>
 */
@Slf4j
@Service
public class AuthRateLimitService {

    private static final String LUA_INCREMENT_WITH_TTL = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(LUA_INCREMENT_WITH_TTL, Long.class);

    private static final int CLEANUP_INTERVAL = 256;

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, LocalBucket> fallback = new ConcurrentHashMap<>();
    private final AtomicLong fallbackOperations = new AtomicLong();
    private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean();

    public AuthRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void check(Operation operation, HttpServletRequest request, String subject) {
        String clientAddress = request == null ? "unknown" : request.getRemoteAddr();
        requireWithin(operation, "ip", clientAddress, operation.ipLimit);
        if (subject != null && !subject.isBlank() && operation.subjectLimit > 0) {
            requireWithin(operation, "subject", normalizeSubject(subject), operation.subjectLimit);
        }
    }

    /** Clear only the account-specific bucket after successful verification. */
    public void clearSubject(Operation operation, String subject) {
        if (subject == null || subject.isBlank() || operation.subjectLimit <= 0) return;
        String key = "auth:limit:" + operation.key + ":subject:"
                + digest(normalizeSubject(subject));
        fallback.remove(key);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ignored) {
            // Redis recovery will eventually expire the fixed-window key.
        }
    }

    private void requireWithin(Operation operation, String scope, String value, int limit) {
        String key = "auth:limit:" + operation.key + ":" + scope + ":" + digest(value);
        long count;
        try {
            Long redisCount = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(operation.windowSeconds));
            if (redisCount == null) {
                throw new IllegalStateException("Redis rate-limit script returned null");
            }
            count = redisCount;
            fallbackWarningLogged.set(false);
        } catch (RuntimeException redisFailure) {
            count = incrementFallback(key, operation.windowSeconds);
            if (fallbackWarningLogged.compareAndSet(false, true)) {
                log.warn("Auth rate limiter using local fallback ({})",
                        redisFailure.getClass().getSimpleName());
            }
        }

        if (count > limit) {
            throw new BizException(429, "请求过于频繁，请稍后重试");
        }
    }

    private long incrementFallback(String key, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        LocalBucket bucket = fallback.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAtEpochSecond <= now) {
                return new LocalBucket(1, now + windowSeconds);
            }
            current.count++;
            return current;
        });
        if ((fallbackOperations.incrementAndGet() % CLEANUP_INTERVAL) == 0) {
            fallback.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochSecond <= now);
        }
        return bucket.count;
    }

    private String normalizeSubject(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public enum Operation {
        CHECK_EMAIL("check-email", 30, 0, 60),
        SEND_CODE("send-code", 20, 5, 60 * 60),
        REGISTER("register", 30, 10, 15 * 60),
        LOGIN("login", 30, 10, 5 * 60),
        WECHAT_LOGIN("wechat-login", 30, 0, 5 * 60),
        RESET_PASSWORD("reset-password", 20, 10, 15 * 60);

        private final String key;
        private final int ipLimit;
        private final int subjectLimit;
        private final long windowSeconds;

        Operation(String key, int ipLimit, int subjectLimit, long windowSeconds) {
            this.key = key;
            this.ipLimit = ipLimit;
            this.subjectLimit = subjectLimit;
            this.windowSeconds = windowSeconds;
        }
    }

    private static final class LocalBucket {
        private long count;
        private final long expiresAtEpochSecond;

        private LocalBucket(long count, long expiresAtEpochSecond) {
            this.count = count;
            this.expiresAtEpochSecond = expiresAtEpochSecond;
        }
    }
}
