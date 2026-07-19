package com.group1.career.controller;

import com.group1.career.aspect.RedactWebLog;
import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.User;
import com.group1.career.service.AuthRateLimitService;
import com.group1.career.service.EmailService;
import com.group1.career.service.UserService;
import com.group1.career.service.VerificationCodeService;
import com.group1.career.utils.JwtUtils;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

@Tag(name = "Auth API", description = "Authentication and Authorization Endpoints")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
@RedactWebLog
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final VerificationCodeService codeService;
    private final AuthRateLimitService rateLimitService;

    // ─────────────────────────────────────────────────────────────
    //  检查邮箱是否已注册
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Check if email is already registered")
    @PostMapping("/check-email")
    public Result<Boolean> checkEmail(@Valid @RequestBody CheckEmailDto request,
                                      HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.getEmail());
        rateLimitService.check(AuthRateLimitService.Operation.CHECK_EMAIL, servletRequest, null);
        boolean exists = userService.isEmailRegistered(email);
        return Result.success(exists);
    }

    // ─────────────────────────────────────────────────────────────
    //  发送验证码（注册 / 找回密码通用）
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Send Email Verification Code")
    @PostMapping("/send-code")
    public Result<String> sendCode(@Valid @RequestBody SendCodeDto request,
                                   HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.getEmail());
        String purpose = request.getPurpose().toUpperCase(Locale.ROOT);
        rateLimitService.check(AuthRateLimitService.Operation.SEND_CODE, servletRequest, email);
        String code = codeService.generateAndStore(email, purpose);
        try {
            // This call is intentionally synchronous: returning "sent" before
            // the SMTP server accepts the message leaves users with a phantom
            // code and an unnecessary resend cooldown.
            emailService.sendVerificationCode(email, code, purpose);
        } catch (RuntimeException ex) {
            codeService.invalidate(email, purpose);
            throw new BizException(503, "验证码发送失败，请稍后重试");
        }
        return Result.success("验证码已发送");
    }

    // ─────────────────────────────────────────────────────────────
    //  注册（需要邮箱验证码）
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Register User with Email Verification")
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterDto request,
                                 HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.getIdentifier());
        rateLimitService.check(AuthRateLimitService.Operation.REGISTER, servletRequest, email);
        boolean valid = codeService.verify(email, "REGISTER", request.getCode());
        if (!valid) {
            return Result.error(400, "Invalid or expired verification code");
        }
        User registered = userService.register(
                request.getNickname(),
                request.getIdentityType(),
                email,
                request.getCredential()
        );
        return Result.success(userService.hydrateUrl(registered));
    }

    // ─────────────────────────────────────────────────────────────
    //  登录
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Login User")
    @PostMapping("/login")
    public Result<LoginResponseDto> login(@Valid @RequestBody LoginDto request,
                                          HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.getIdentifier());
        rateLimitService.check(AuthRateLimitService.Operation.LOGIN, servletRequest, email);
        User user = userService.login(
                request.getIdentityType(),
                email,
                request.getCredential()
        );
        rateLimitService.clearSubject(AuthRateLimitService.Operation.LOGIN, email);
        String token = JwtUtils.generateToken(
                user.getUserId(), "USER", normalizedAuthVersion(user));
        return Result.success(new LoginResponseDto(token, userService.hydrateUrl(user)));
    }

    // ─────────────────────────────────────────────────────────────
    //  重置密码（需要邮箱验证码）
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Reset Password with Email Verification Code")
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@Valid @RequestBody ResetPasswordDto request,
                                        HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.getEmail());
        rateLimitService.check(AuthRateLimitService.Operation.RESET_PASSWORD, servletRequest, email);
        boolean valid = codeService.verify(email, "RESET", request.getCode());
        if (!valid) {
            return Result.error(400, "Invalid or expired verification code");
        }
        userService.resetPassword(email, request.getNewPassword());
        rateLimitService.clearSubject(AuthRateLimitService.Operation.RESET_PASSWORD, email);
        return Result.success("Password reset successfully");
    }

    @Operation(summary = "Change the authenticated user's password")
    @PostMapping("/change-password")
    public Result<String> changePassword(@Valid @RequestBody ChangePasswordDto request) {
        userService.changePassword(
                SecurityUtil.requireCurrentUserId(),
                request.getCurrentPassword(),
                request.getNewPassword());
        return Result.success("Password changed successfully. Please sign in again.");
    }

    @Operation(summary = "Sign out and revoke all existing sessions")
    @PostMapping("/logout")
    public Result<String> logout() {
        userService.revokeSessions(SecurityUtil.requireCurrentUserId());
        return Result.success("Signed out successfully");
    }

    // ─────────────────────────────────────────────────────────────
    //  微信登录
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "WeChat Login")
    @PostMapping("/wechat-login")
    public Result<LoginResponseDto> wechatLogin(@Valid @RequestBody WeChatLoginDto request,
                                                HttpServletRequest servletRequest) {
        rateLimitService.check(AuthRateLimitService.Operation.WECHAT_LOGIN, servletRequest, null);
        User user = userService.wechatLogin(request.getCode());
        String token = JwtUtils.generateToken(
                user.getUserId(), "USER", normalizedAuthVersion(user));
        return Result.success(new LoginResponseDto(token, userService.hydrateUrl(user)));
    }

    // ─────────────────────────────────────────────────────────────
    //  DTO classes
    // ─────────────────────────────────────────────────────────────

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private long normalizedAuthVersion(User user) {
        return user.getAuthVersion() == null ? 0L : user.getAuthVersion();
    }

    @Data
    public static class LoginResponseDto {
        private String token;
        private User user;
        /** True when this verified login cancelled a pending account deletion. */
        private boolean accountRestored;

        public LoginResponseDto(String token, User user) {
            this.token = token;
            this.user = user;
            this.accountRestored = user != null && Boolean.TRUE.equals(user.getAccountRestored());
        }
    }

    @Data
    public static class CheckEmailDto {
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        @Size(max = 254, message = "Email is too long")
        private String email;
    }

    @Data
    public static class SendCodeDto {
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        @Size(max = 254, message = "Email is too long")
        private String email;

        /** REGISTER or RESET */
        @NotBlank(message = "Purpose cannot be blank")
        @Pattern(regexp = "REGISTER|RESET", message = "Purpose must be REGISTER or RESET")
        private String purpose;
    }

    @Data
    public static class RegisterDto {
        @NotBlank(message = "Nickname cannot be blank")
        @Size(min = 2, max = 20, message = "Nickname must be between 2 and 20 characters")
        private String nickname;

        @NotBlank(message = "Identity Type is required")
        @Pattern(regexp = "EMAIL_PASSWORD", message = "Unsupported identity type")
        private String identityType;

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        @Size(max = 254, message = "Email is too long")
        private String identifier;

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
        private String credential;

        @NotBlank(message = "Verification code cannot be blank")
        @Pattern(regexp = "\\d{6}", message = "Verification code must be 6 digits")
        private String code;
    }

    @Data
    public static class LoginDto {
        @NotBlank(message = "Identity Type is required")
        @Pattern(regexp = "EMAIL_PASSWORD", message = "Unsupported identity type")
        private String identityType;

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        @Size(max = 254, message = "Email is too long")
        private String identifier;

        @NotBlank(message = "Password cannot be blank")
        @Size(max = 128, message = "Password is too long")
        private String credential;
    }

    @Data
    public static class ResetPasswordDto {
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        @Size(max = 254, message = "Email is too long")
        private String email;

        @NotBlank(message = "Verification code cannot be blank")
        @Pattern(regexp = "\\d{6}", message = "Verification code must be 6 digits")
        private String code;

        @NotBlank(message = "New password cannot be blank")
        @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
        private String newPassword;
    }

    @Data
    public static class ChangePasswordDto {
        @NotBlank(message = "Current password cannot be blank")
        @Size(max = 128, message = "Current password is too long")
        private String currentPassword;

        @NotBlank(message = "New password cannot be blank")
        @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
        private String newPassword;
    }

    @Data
    public static class WeChatLoginDto {
        @NotBlank(message = "Code cannot be blank")
        @Size(max = 128, message = "Code is too long")
        private String code;
    }
}
