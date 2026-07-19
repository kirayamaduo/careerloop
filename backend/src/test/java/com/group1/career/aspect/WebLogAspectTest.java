package com.group1.career.aspect;

import com.group1.career.common.Result;
import com.group1.career.controller.AuthController;
import com.group1.career.controller.CareerPlatformBridgeController;
import com.group1.career.controller.IntegrationLinkController;
import com.group1.career.model.dto.CareerBridgeDtos.ConsumeLinkCodeRequest;
import com.group1.career.model.dto.CareerBridgeDtos.LinkCodeResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class WebLogAspectTest {

    private final WebLogAspect aspect = new WebLogAspect();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void bridgeIssueResponseAndConsumeRequestNeverLogOneTimeCodes(
            CapturedOutput output) throws Exception {
        String issuedCode = "73194628";
        Method issueMethod = IntegrationLinkController.class.getMethod("issueLinkCode");
        aspect.doAfterReturning(
                joinPoint(issueMethod),
                Result.success(LinkCodeResponse.builder()
                        .code(issuedCode)
                        .expiresInSeconds(600)
                        .build()));

        String consumedCode = "86420713";
        ConsumeLinkCodeRequest request = new ConsumeLinkCodeRequest(consumedCode);
        bindRequest("POST", "/internal/career-platform/v1/link-code/consume");
        Method consumeMethod = CareerPlatformBridgeController.class.getMethod(
                "consumeLinkCode",
                ConsumeLinkCodeRequest.class);
        aspect.doBefore(joinPoint(consumeMethod, request));

        assertThat(output.getOut())
                .contains("Response Args  : [REDACTED]")
                .contains("Request Args   : [REDACTED]")
                .doesNotContain(issuedCode, consumedCode);
    }

    @Test
    void authControllerNeverLogsCredentialsOrTokens(CapturedOutput output) throws Exception {
        String password = "do-not-log-this-password";
        AuthController.LoginDto login = new AuthController.LoginDto();
        login.setIdentityType("EMAIL");
        login.setIdentifier("student@example.com");
        login.setCredential(password);
        bindRequest("POST", "/auth/login");
        Method loginMethod = AuthController.class.getMethod(
                "login",
                AuthController.LoginDto.class);
        JoinPoint joinPoint = joinPoint(loginMethod, login);

        String token = "do-not-log-this-jwt";
        aspect.doBefore(joinPoint);
        aspect.doAfterReturning(
                joinPoint,
                Result.success(new AuthController.LoginResponseDto(token, null)));

        assertThat(output.getOut())
                .contains("Request Args   : [REDACTED]")
                .contains("Response Args  : [REDACTED]")
                .doesNotContain(password, token);
    }

    @Test
    void unannotatedControllerPayloadsAreAlsoRedacted(CapturedOutput output) throws Exception {
        String privatePassportData = "private-resume-and-interview-data";
        Method passportMethod = CareerPlatformBridgeController.class.getMethod(
                "getStudentPassport",
                Long.class);
        JoinPoint joinPoint = joinPoint(passportMethod, 42L);
        bindRequest("GET", "/internal/career-platform/v1/students/42/passport");

        aspect.doBefore(joinPoint);
        aspect.doAfterReturning(joinPoint, Result.success(privatePassportData));

        assertThat(output.getOut())
                .contains("Request Args   : [REDACTED]")
                .contains("Response Args  : [REDACTED]")
                .doesNotContain(privatePassportData);
    }

    private static JoinPoint joinPoint(Method method, Object... args) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(method.getDeclaringClass().getName());
        when(signature.getName()).thenReturn(method.getName());
        return joinPoint;
    }

    private static void bindRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
