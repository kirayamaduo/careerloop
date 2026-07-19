package com.group1.career.service.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.group1.career.service.AiService;
import com.group1.career.service.ai.tools.AiTool;
import com.group1.career.service.ai.tools.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FunctionCallingServiceImplPrivacyTest {

    private AiService aiService;
    private ToolRegistry toolRegistry;
    private FunctionCallingServiceImpl service;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        toolRegistry = mock(ToolRegistry.class);
        service = new FunctionCallingServiceImpl(aiService, toolRegistry);
        when(toolRegistry.buildToolSchemas()).thenReturn(List.of());

        logger = (Logger) LoggerFactory.getLogger(FunctionCallingServiceImpl.class);
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
    void malformedModelBodyIsNotLoggedOrReturned() {
        String sensitiveBody = "not-json private resume and prompt";
        when(aiService.chatWithTools(anyList(), anyList())).thenReturn(sensitiveBody);

        String result = service.chat(List.of(Map.of("role", "user", "content", "hello")), 7L);
        String logs = renderedLogs();

        assertEquals(AiService.UNAVAILABLE_MESSAGE, result);
        assertFalse(result.contains(sensitiveBody));
        assertFalse(logs.contains(sensitiveBody));
        assertTrue(logs.contains("exception=JsonParseException"));
    }

    @Test
    void toolExceptionBecomesStableDetailFreeModelResult() {
        String sensitiveMessage = "raw resume contents from database";
        AiTool tool = mock(AiTool.class);
        when(toolRegistry.find("resume_lookup")).thenReturn(Optional.of(tool));
        when(tool.execute(Map.of(), 7L)).thenThrow(new IllegalStateException(sensitiveMessage));

        String result = ReflectionTestUtils.invokeMethod(
                service, "executeTool", "resume_lookup", "{}", 7L);
        String logs = renderedLogs();

        assertEquals("{\"ok\":false,\"error\":\"TOOL_UNAVAILABLE\"}", result);
        assertFalse(result.contains(sensitiveMessage));
        assertFalse(logs.contains(sensitiveMessage));
        assertTrue(logs.contains("exception=IllegalStateException"));
    }

    private String renderedLogs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
