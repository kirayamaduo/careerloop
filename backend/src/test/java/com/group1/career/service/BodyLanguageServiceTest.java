package com.group1.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class BodyLanguageServiceTest {

    private BodyLanguageService service;

    @BeforeEach
    void setUp() {
        service = spy(new BodyLanguageService(new ObjectMapper()));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "sidecarUrl", "http://body-lang:8001");
    }

    @Test
    @DisplayName("No-face and incomplete sidecar responses are never averaged")
    void invalidSignalsAreDiscardedWithoutDefaults() throws Exception {
        assertNull(service.parseSidecarResponse("""
                {"valid":false,"eye_contact":null,"expression":null,"posture":null,
                 "confidence":0.0,"note":"no-face-detected"}
                """));
        assertNull(service.parseSidecarResponse("""
                {"eye_contact":88,"expression":77,"posture":66,"confidence":0.9}
                """), "legacy responses without explicit valid=true must fail closed");
        assertNull(service.parseSidecarResponse("""
                {"valid":true,"eye_contact":88,"expression":77,
                 "confidence":0.9,"note":null}
                """), "a missing score must not silently become 60");
        assertNull(service.parseSidecarResponse("""
                {"valid":true,"eye_contact":88,"expression":77,"posture":66,
                 "confidence":0.2,"note":null}
                """), "low-confidence frames must not enter the aggregate");
        assertNull(service.parseSidecarResponse("""
                {"valid":true,"eye_contact":88,"expression":77,"posture":66,
                 "confidence":0.9,"note":"incomplete-landmarks"}
                """), "an invalid sidecar note must fail closed even if valid was set incorrectly");
        assertNull(service.parseSidecarResponse("""
                {"valid":true,"eye_contact":"88","expression":77,"posture":66,
                 "confidence":0.9,"note":null}
                """), "text scores must not be silently coerced to numbers");
        assertNull(service.parseSidecarResponse("""
                {"valid":"true","eye_contact":88,"expression":77,"posture":66,
                 "confidence":0.9,"note":null}
                """), "the explicit validity signal must be a JSON boolean");
        assertNull(service.parseSidecarResponse("""
                {"valid":true,"eye_contact":88.9,"expression":77,"posture":66,
                 "confidence":0.9,"note":null}
                """), "fractional scores must not be silently truncated");
    }

    @Test
    @DisplayName("Only explicit, complete, sufficiently confident scores are accepted")
    void validSignalIsParsedExactly() throws Exception {
        BodyLanguageService.FrameScore score = service.parseSidecarResponse("""
                {"valid":true,"eye_contact":88,"expression":77,"posture":66,
                 "confidence":0.8,"note":null}
                """);

        assertNotNull(score);
        assertEquals(88, score.getEyeContact());
        assertEquals(77, score.getExpression());
        assertEquals(66, score.getPosture());
        assertEquals(0.8, score.getConfidence(), 0.0001);
    }

    @Test
    @DisplayName("Oversize Base64 is rejected before any sidecar call")
    void oversizeFrameIsRejectedBeforeSidecar() throws Exception {
        String oversized = "A".repeat(BodyLanguageService.MAX_BASE64_CHARS + 1);

        BodyLanguageService.SubmissionResult result =
                service.recordFrame(7L, 9L, oversized);

        assertEquals(BodyLanguageService.SubmissionResult.TOO_LARGE, result);
        verify(service, never()).callSidecar(anyLong(), anyString());
    }

    @Test
    @DisplayName("Per-user sampling accepts one frame then rate-limits immediate retry")
    void perUserRateLimitIsEnforced() throws Exception {
        doReturn(score()).when(service).callSidecar(anyLong(), anyString());

        assertEquals(BodyLanguageService.SubmissionResult.ACCEPTED,
                service.recordFrame(7L, 9L, "frame-one"));
        assertEquals(BodyLanguageService.SubmissionResult.RATE_LIMITED,
                service.recordFrame(7L, 9L, "frame-two"));
    }

    @Test
    @DisplayName("A slow request cannot overlap another request from the same user")
    void sameUserSlowRequestCannotOverlap() throws Exception {
        CountDownLatch enteredSidecar = new CountDownLatch(1);
        CountDownLatch releaseSidecar = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredSidecar.countDown();
            assertTrue(releaseSidecar.await(3, TimeUnit.SECONDS));
            return score();
        }).when(service).callSidecar(anyLong(), anyString());

        CompletableFuture<BodyLanguageService.SubmissionResult> first =
                CompletableFuture.supplyAsync(() -> service.recordFrame(7L, 9L, "frame-one"));
        assertTrue(enteredSidecar.await(2, TimeUnit.SECONDS));

        assertEquals(BodyLanguageService.SubmissionResult.BUSY,
                service.recordFrame(7L, 9L, "frame-two"));

        releaseSidecar.countDown();
        assertEquals(BodyLanguageService.SubmissionResult.ACCEPTED, first.get(2, TimeUnit.SECONDS));
    }

    private BodyLanguageService.FrameScore score() {
        return BodyLanguageService.FrameScore.builder()
                .eyeContact(80)
                .expression(75)
                .posture(70)
                .confidence(0.8)
                .build();
    }
}
