package com.group1.career.config;

import com.group1.career.interceptor.CareerBridgeInterceptor;
import com.group1.career.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CareerBridgeInterceptor careerBridgeInterceptor;
    private final String[] allowedOriginPatterns;

    public WebConfig(AuthInterceptor authInterceptor,
                     CareerBridgeInterceptor careerBridgeInterceptor,
                     @Value("${cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
                     String allowedOriginPatterns) {
        this.authInterceptor = authInterceptor;
        this.careerBridgeInterceptor = careerBridgeInterceptor;
        this.allowedOriginPatterns = java.util.Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank() && !"*".equals(origin))
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOriginPatterns.length == 0) return;
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                // Authentication is carried in an Authorization header, never
                // a browser cookie, so cross-origin credentials are unnecessary.
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        // Mandatory dependency + unconditional registration keeps the bridge
        // fail-closed: the application cannot start with an unguarded internal
        // route if this dedicated interceptor is ever removed or misconfigured.
        registry.addInterceptor(careerBridgeInterceptor)
                .addPathPatterns("/internal/career-platform/v1/**");

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login", "/auth/register", "/auth/wechat-login",
                        "/auth/send-code", "/auth/reset-password", "/auth/check-email",
                        // Homepage content is public, but personalised content and
                        // the expensive manual refresh endpoint are authenticated.
                        // Keep this list explicit so a future /api/homepage route
                        // cannot accidentally inherit public access.
                        "/api/homepage/feed",
                        "/api/homepage/articles/*/cover",
                        "/api/assessments/scales",
                        "/api/assessments/scales/*/questions",
                        // plan 接口需要登录态（requireCurrentUserId），不排除
                        "/api/careers/paths/**",
                        // Internal server-to-server bridge has its own dedicated
                        // X-Career-Bridge-Key interceptor. It must never fall
                        // through to the end-user JWT interceptor.
                        "/internal/career-platform/v1/**",
                        // Liveness/readiness probes used by Docker, nginx, uptime
                        // monitors -- and us, when proving the ngrok tunnel reaches
                        // the backend before pointing the mini-program at it.
                        "/api/health",
                        "/actuator/**",
                        "/doc.html", "/webjars/**", "/swagger-resources/**", "/v3/api-docs/**"
                );
    }
}
