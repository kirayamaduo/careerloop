package com.group1.career.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Fail-closed authentication for the Career Platform server-to-server bridge.
 *
 * <p>This deliberately does not reuse the end-user JWT path. The shared key is
 * supplied only through {@code CAREER_PLATFORM_BRIDGE_KEY}; an absent key
 * disables all internal bridge endpoints rather than creating an insecure
 * default.</p>
 */
@Component
public class CareerBridgeInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Career-Bridge-Key";

    private final String configuredKey;

    public CareerBridgeInterceptor(@Value("${career.bridge.key:}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String provided = request.getHeader(HEADER_NAME);
        if (!configuredKey.isBlank() && provided != null && constantTimeEquals(configuredKey, provided)) {
            return true;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}");
        return false;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
