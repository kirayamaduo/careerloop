package com.group1.career.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceImplPrivacyTest {

    private HttpClient httpClient;
    private AiServiceImpl service;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        service = new AiServiceImpl();
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "modelName", "test-model");
        ReflectionTestUtils.setField(service, "fallbackMode", false);

        logger = (Logger) LoggerFactory.getLogger(AiServiceImpl.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void upstreamErrorBodyIsNeitherLoggedNorReturned() throws Exception {
        String sensitiveBody = "private resume text and upstream diagnostic";
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(502);
        when(response.body()).thenReturn(sensitiveBody);
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("x-request-id", List.of("req-safe-123")), (name, value) -> true));
        doReturn(response).when(httpClient).send(
                any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        String result = service.chatWithTools(List.of(), List.of());
        String logs = renderedLogs();

        assertEquals(AiServiceImpl.TOOLS_ERROR_JSON, result);
        assertFalse(result.contains(sensitiveBody));
        assertFalse(logs.contains(sensitiveBody));
        assertTrue(logs.contains("status=502"));
        assertTrue(logs.contains("requestId=req-safe-123"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void exceptionMessageIsNeitherLoggedNorReturned() throws Exception {
        String sensitiveMessage = "prompt and credential accidentally embedded here";
        doThrow(new IOException(sensitiveMessage)).when(httpClient).send(
                any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        String result = service.chatWithTools(List.of(), List.of());
        String logs = renderedLogs();

        assertEquals(AiServiceImpl.TOOLS_ERROR_JSON, result);
        assertFalse(result.contains(sensitiveMessage));
        assertFalse(logs.contains(sensitiveMessage));
        assertTrue(logs.contains("exception=IOException"));
    }

    private String renderedLogs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
