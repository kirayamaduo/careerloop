package com.group1.career.service;

import com.group1.career.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private HttpServletRequest request;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("offline"));
    }

    @Test
    void redisFailureStillEnforcesPerSubjectLimitLocally() {
        AuthRateLimitService service = new AuthRateLimitService(redisTemplate);

        for (int attempt = 0; attempt < 10; attempt++) {
            assertDoesNotThrow(() -> service.check(
                    AuthRateLimitService.Operation.LOGIN, request, "Alice@Example.com"));
        }
        BizException exception = assertThrows(BizException.class, () -> service.check(
                AuthRateLimitService.Operation.LOGIN, request, " alice@example.COM "));
        assertEquals(429, exception.getCode());
    }

    @Test
    void successfulAuthenticationCanClearOnlyTheSubjectBucket() {
        AuthRateLimitService service = new AuthRateLimitService(redisTemplate);
        for (int attempt = 0; attempt < 10; attempt++) {
            service.check(AuthRateLimitService.Operation.LOGIN, request, "alice@example.com");
        }

        service.clearSubject(AuthRateLimitService.Operation.LOGIN, "Alice@Example.COM");

        assertDoesNotThrow(() -> service.check(
                AuthRateLimitService.Operation.LOGIN, request, "alice@example.com"));
    }

    @Test
    void ipLimitBoundsRotatingSubjectsDuringRedisOutage() {
        AuthRateLimitService service = new AuthRateLimitService(redisTemplate);

        for (int attempt = 0; attempt < 20; attempt++) {
            String email = "person" + attempt + "@example.com";
            assertDoesNotThrow(() -> service.check(
                    AuthRateLimitService.Operation.SEND_CODE, request, email));
        }
        BizException exception = assertThrows(BizException.class, () -> service.check(
                AuthRateLimitService.Operation.SEND_CODE, request, "next@example.com"));
        assertEquals(429, exception.getCode());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void redisCounterAboveLimitIsRejected() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(31L);
        AuthRateLimitService service = new AuthRateLimitService(redisTemplate);

        BizException exception = assertThrows(BizException.class, () -> service.check(
                AuthRateLimitService.Operation.CHECK_EMAIL, request, null));
        assertEquals(429, exception.getCode());
    }
}
