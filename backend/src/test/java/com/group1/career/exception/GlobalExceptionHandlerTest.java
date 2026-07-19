package com.group1.career.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.group1.career.common.ErrorCode;
import com.group1.career.common.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Unexpected exception details are logged but never returned to clients")
    void unexpectedExceptionDoesNotLeakInternalMessage() {
        String sensitiveDetail = "jdbc:mysql://internal-db/career?password=secret";
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        Level previousLevel = logger.getLevel();

        Result<?> result;
        try {
            // The production handler must log the exception. Suppress that
            // intentional stack trace only in this unit test's console output.
            logger.setLevel(Level.OFF);
            result = handler.handleException(new RuntimeException(sensitiveDetail));
        } finally {
            logger.setLevel(previousLevel);
        }

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), result.getMessage());
        assertNull(result.getData());
        assertFalse(result.getMessage().contains(sensitiveDetail));
    }
}
