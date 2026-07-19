package com.group1.career.config;

import com.group1.career.service.BodyLanguageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reject known-oversize frame requests before DispatcherServlet/Jackson reads
 * their Base64 body. Jackson's streaming string constraint is the second line
 * of defence for chunked requests without Content-Length.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BodyLanguageRequestSizeFilter extends OncePerRequestFilter {

    static final long MAX_REQUEST_BYTES = BodyLanguageService.MAX_BASE64_CHARS + 32L * 1024L;
    private static final byte[] TOO_LARGE_BODY =
            "{\"code\":413,\"message\":\"Frame payload is too large\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/body-language/frame".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_REQUEST_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setContentLength(TOO_LARGE_BODY.length);
            response.getOutputStream().write(TOO_LARGE_BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
