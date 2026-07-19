package com.group1.career.job;

import com.group1.career.repository.UserRepository;
import com.group1.career.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F25: Nightly hard-deletion of accounts whose 30-day grace period has expired.
 *
 * <p>Runs every night at 03:00 server time. Finds users with
 * {@code deleted_at <= now() - 30 days} and deletes all their personal data
 * (PII) while keeping only an anonymized {@code account_deletion_log} row for
 * compliance.</p>
 *
 * <p>Each account runs in its own {@code REQUIRES_NEW} transaction, so one
 * malformed legacy row or transient OSS failure cannot roll back the rest of
 * the nightly batch.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionCleanupJob {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    private static final int GRACE_DAYS = 30;

    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(GRACE_DAYS);
        List<Long> expiredIds = userRepository.findExpiredDeletionIds(cutoff);
        if (expiredIds.isEmpty()) return;

        log.info("[F25] Permanently erasing {} expired accounts", expiredIds.size());

        for (Long userId : expiredIds) {
            try {
                boolean erased = accountDeletionService.hardDeleteExpiredUser(userId, cutoff);
                if (erased) {
                    log.info("[F25] Permanently erased one expired account");
                }
            } catch (Exception e) {
                // Do not copy a raw account id into durable application logs.
                log.error("[F25] Failed to erase one expired account; it will be retried", e);
            }
        }
    }
}
