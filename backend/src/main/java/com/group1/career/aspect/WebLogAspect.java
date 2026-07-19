package com.group1.career.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
@Slf4j
public class WebLogAspect {

    @Pointcut("execution(public * com.group1.career.controller..*.*(..))")
    public void webLog() {
    }

    @Before("webLog()")
    public void doBefore(JoinPoint joinPoint) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            log.info("========================================== Start ==========================================");
            log.info("URL            : {}", request.getRequestURL().toString());
            log.info("HTTP Method    : {}", request.getMethod());
            log.info("Class Method   : {}.{}", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
            log.info("IP             : {}", request.getRemoteAddr());
            // Controller payloads can contain passwords, verification codes,
            // resumes, chat messages and interview media. Keep the operational
            // request metadata above, but never serialize user data into logs.
            log.info("Request Args   : [REDACTED]");
        }
    }

    @AfterReturning(returning = "ret", pointcut = "webLog()")
    public void doAfterReturning(JoinPoint joinPoint, Object ret) {
        log.info("Response Args  : [REDACTED]");
        log.info("=========================================== End ===========================================");
    }
}
