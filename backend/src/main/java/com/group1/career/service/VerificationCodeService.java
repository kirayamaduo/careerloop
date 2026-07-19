package com.group1.career.service;

import com.group1.career.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内存式验证码服务。
 * 每个邮箱+用途组合独立管理，支持：过期清理、防刷（60秒冷却）、最多验证3次。
 */
@Slf4j
@Service
public class VerificationCodeService {

    private static final long EXPIRE_MS = 5 * 60 * 1000L;     // 5 分钟有效
    private static final long COOLDOWN_MS = 60 * 1000L;        // 60 秒发送冷却
    private static final int MAX_ATTEMPTS = 5;                  // 最多验证 5 次

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, CodeEntry> store = new ConcurrentHashMap<>();

    public String generateAndStore(String email, String purpose) {
        String normalizedPurpose = normalizePurpose(purpose);
        String key = buildKey(email, normalizedPurpose);
        long now = Instant.now().toEpochMilli();
        String code = String.format("%06d", random.nextInt(1_000_000));
        store.compute(key, (ignored, existing) -> {
            if (existing != null && !existing.isExpiredAt(now)) {
                long remaining = COOLDOWN_MS - (now - existing.createdAt);
                if (remaining > 0) {
                    long waitSeconds = Math.max(1, (remaining + 999) / 1000);
                    throw new BizException(429, "请等待 " + waitSeconds + " 秒后再发送");
                }
            }
            return new CodeEntry(code, now);
        });
        log.info("Generated verification code for {} [{}]",
                maskEmail(email.trim().toLowerCase(Locale.ROOT)), normalizedPurpose);
        return code;
    }

    /**
     * 验证验证码，验证成功后立即作废。
     */
    public boolean verify(String email, String purpose, String inputCode) {
        String key = buildKey(email, normalizePurpose(purpose));
        long now = Instant.now().toEpochMilli();
        AtomicBoolean verified = new AtomicBoolean(false);
        store.computeIfPresent(key, (ignored, entry) -> {
            if (entry.isExpiredAt(now) || entry.attempts >= MAX_ATTEMPTS) {
                return null;
            }
            entry.attempts++;
            if (entry.code.equals(inputCode)) {
                verified.set(true);
                return null;
            }
            // The fifth failed guess exhausts the code immediately.
            return entry.attempts >= MAX_ATTEMPTS ? null : entry;
        });
        return verified.get();
    }

    /**
     * Remove a generated code when its delivery failed so the user can retry
     * immediately instead of being locked behind the resend cooldown.
     */
    public void invalidate(String email, String purpose) {
        store.remove(buildKey(email, normalizePurpose(purpose)));
    }

    private String buildKey(String email, String purpose) {
        if (email == null || email.isBlank()) {
            throw new BizException(400, "邮箱不能为空");
        }
        return email.toLowerCase(Locale.ROOT).trim() + ":" + purpose;
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!"REGISTER".equals(normalized) && !"RESET".equals(normalized)) {
            throw new BizException(400, "Purpose must be REGISTER or RESET");
        }
        return normalized;
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** 每 10 分钟清理过期条目，防止内存泄漏 */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void evictExpired() {
        int removed = 0;
        for (var it = store.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().isExpiredAt(Instant.now().toEpochMilli())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Evicted {} expired verification codes", removed);
        }
    }

    private static class CodeEntry {
        final String code;
        final long createdAt;
        int attempts = 0;

        CodeEntry(String code, long createdAt) {
            this.code = code;
            this.createdAt = createdAt;
        }

        boolean isExpiredAt(long now) {
            return now - createdAt > EXPIRE_MS;
        }
    }
}
