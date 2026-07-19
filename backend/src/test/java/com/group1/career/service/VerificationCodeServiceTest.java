package com.group1.career.service;

import com.group1.career.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationCodeServiceTest {

    @Test
    void emailAndPurposeAreNormalizedAcrossOperations() {
        VerificationCodeService service = new VerificationCodeService();
        String code = service.generateAndStore("  Alice@Example.COM ", "register");

        assertTrue(service.verify("alice@example.com", "REGISTER", code));
        assertFalse(service.verify("alice@example.com", "REGISTER", code));
    }

    @Test
    void fifthWrongGuessImmediatelyInvalidatesCode() {
        VerificationCodeService service = new VerificationCodeService();
        String code = service.generateAndStore("alice@example.com", "RESET");
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertFalse(service.verify("alice@example.com", "RESET", wrong));
        }
        assertFalse(service.verify("alice@example.com", "RESET", code));
    }

    @Test
    void unsupportedPurposeIsRejectedInsideService() {
        VerificationCodeService service = new VerificationCodeService();

        BizException exception = assertThrows(BizException.class,
                () -> service.generateAndStore("alice@example.com", "LOGIN"));
        assertEquals(400, exception.getCode());
    }

    @Test
    void concurrentGenerationAllowsOnlyOneCodeInsideCooldown() throws Exception {
        VerificationCodeService service = new VerificationCodeService();
        int workers = 8;
        var executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                try {
                    service.generateAndStore("alice@example.com", "REGISTER");
                    return true;
                } catch (BizException limited) {
                    assertEquals(429, limited.getCode());
                    return false;
                }
            });
        }

        List<Future<Boolean>> results = tasks.stream().map(executor::submit).toList();
        ready.await();
        start.countDown();
        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) successes++;
        }
        executor.shutdownNow();

        assertEquals(1, successes);
    }
}
