package com.group1.career.config;

import com.group1.career.interceptor.CareerBridgeInterceptor;
import com.group1.career.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CareerBridgeInterceptor careerBridgeInterceptor;

    public WebConfig(AuthInterceptor authInterceptor,
                     CareerBridgeInterceptor careerBridgeInterceptor) {
        this.authInterceptor = authInterceptor;
        this.careerBridgeInterceptor = careerBridgeInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true);
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
                        "/api/homepage/**",
                        "/api/assessments/scales",
                        "/api/assessments/scales/*/questions",
                        // plan 接口需要登录态（requireCurrentUserId），不排除
                        "/api/careers/paths/**",
                        "/api/careers/progress/**",
                        "/api/careers/timeline",
                        "/api/careers/initialize",
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
