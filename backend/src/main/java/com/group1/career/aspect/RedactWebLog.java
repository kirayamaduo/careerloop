package com.group1.career.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prevents controller request and response payloads from being written to the
 * general web log. URL, HTTP method, controller method, and client IP are still
 * logged so that operational tracing remains available.
 *
 * <p>Use this on endpoints whose payload contains credentials, one-time codes,
 * tokens, or other secrets. It may be applied to an individual controller
 * method or to an entire controller class.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedactWebLog {
}
