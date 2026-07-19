package com.group1.career.exception;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.group1.career.common.ErrorCode;
import com.group1.career.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleUnreadableRequest(HttpMessageNotReadableException e) {
        if (hasCause(e, StreamConstraintsException.class)) {
            log.warn("Rejected JSON request that exceeded streaming constraints");
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Result.error(413, "Request payload is too large"));
        }
        log.warn("Malformed JSON request");
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCode.PARAM_ERROR));
    }

    /**
     * Handle Custom Business Exception
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("Business Exception: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * Handle JSR-303 @Valid validation failures on @RequestBody
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        log.warn("Validation Error: {}", message);
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * Handle @Validated constraint violations on method parameters
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("Constraint Violation: {}", e.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * Handle Global Exception
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("System Error", e);
        // Keep stack traces in server logs, never in the public response. JDBC,
        // HTTP-client and cloud-SDK exception messages can contain SQL, internal
        // hosts, bucket names, credentials, or upstream response fragments.
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
