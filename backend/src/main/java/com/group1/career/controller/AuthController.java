package com.group1.career.controller;

import com.group1.career.aspect.RedactWebLog;
import com.group1.career.common.Result;
import com.group1.career.model.entity.User;
import com.group1.career.service.EmailService;
import com.group1.career.service.UserService;
import com.group1.career.service.VerificationCodeService;
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
import jakarta.validation.constraints.Size;

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

    // ─────────────────────────────────────────────────────────────
    //  检查邮箱是否已注册
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Check if email is already registered")
    @PostMapping("/check-email")
    public Result<Boolean> checkEmail(@Valid @RequestBody CheckEmailDto request) {
        boolean exists = userService.isEmailRegistered(request.getEmail());
        return Result.success(exists);
    }

    // ─────────────────────────────────────────────────────────────
    //  发送验证码（注册 / 找回密码通用）
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Send Email Verification Code")
    @PostMapping("/send-code")
    public Result<String> sendCode(@Valid @RequestBody SendCodeDto request) {
        String code = codeService.generateAndStore(request.getEmail(), request.getPurpose());
        emailService.sendVerificationCode(request.getEmail(), code, request.getPurpose());
        return Result.success("Verification code sent to " + request.getEmail());
    }

    // ─────────────────────────────────────────────────────────────
    //  注册（需要邮箱验证码）
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Register User with Email Verification")
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterDto request) {
        boolean valid = codeService.verify(request.getIdentifier(), "REGISTER", request.getCode());
        if (!valid) {
            return Result.error(400, "Invalid or expired verification code");
        }
        User registered = userService.register(
                request.getNickname(),
                request.getIdentityType(),
                request.getIdentifier(),
                request.getCredential()
        );
        return Result.success(userService.hydrateUrl(registered));
    }

    // ─────────────────────────────────────────────────────────────
    //  登录
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Login User")
    @PostMapping("/login")
    public Result<LoginResponseDto> login(@Valid @RequestBody LoginDto request) {
        User user = userService.login(
                request.getIdentityType(),
                request.getIdentifier(),
                request.getCredential()
        );
        String token = com.group1.career.utils.JwtUtils.generateToken(user.getUserId(), "USER");
        return Result.success(new LoginResponseDto(token, userService.hydrateUrl(user)));
    }

    // ─────────────────────────────────────────────────────────────
    //  重置密码（需要邮箱验证码）
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Reset Password with Email Verification Code")
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@Valid @RequestBody ResetPasswordDto request) {
        boolean valid = codeService.verify(request.getEmail(), "RESET", request.getCode());
        if (!valid) {
            return Result.error(400, "Invalid or expired verification code");
        }
        userService.resetPassword(request.getEmail(), request.getNewPassword());
        return Result.success("Password reset successfully");
    }

    // ─────────────────────────────────────────────────────────────
    //  微信登录
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "WeChat Login")
    @PostMapping("/wechat-login")
    public Result<LoginResponseDto> wechatLogin(@Valid @RequestBody WeChatLoginDto request) {
        User user = userService.wechatLogin(request.getCode());
        String token = com.group1.career.utils.JwtUtils.generateToken(user.getUserId(), "USER");
        return Result.success(new LoginResponseDto(token, userService.hydrateUrl(user)));
    }

    // ─────────────────────────────────────────────────────────────
    //  DTO classes
    // ─────────────────────────────────────────────────────────────

    @Data
    public static class LoginResponseDto {
        private String token;
        private User user;

        public LoginResponseDto(String token, User user) {
            this.token = token;
            this.user = user;
        }
    }

    @Data
    public static class CheckEmailDto {
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        private String email;
    }

    @Data
    public static class SendCodeDto {
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        private String email;

        /** REGISTER or RESET */
        @NotBlank(message = "Purpose cannot be blank")
        private String purpose;
    }

    @Data
    public static class RegisterDto {
        @NotBlank(message = "Nickname cannot be blank")
        @Size(min = 2, max = 20, message = "Nickname must be between 2 and 20 characters")
        private String nickname;

        @NotBlank(message = "Identity Type is required")
        private String identityType;

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        private String identifier;

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String credential;

        @NotBlank(message = "Verification code cannot be blank")
        private String code;
    }

    @Data
    public static class LoginDto {
        @NotBlank(message = "Identity Type is required")
        private String identityType;

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        private String identifier;

        @NotBlank(message = "Password cannot be blank")
        private String credential;
    }

    @Data
    public static class ResetPasswordDto {
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        private String email;

        @NotBlank(message = "Verification code cannot be blank")
        private String code;

        @NotBlank(message = "New password cannot be blank")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String newPassword;
    }

    @Data
    public static class WeChatLoginDto {
        @NotBlank(message = "Code cannot be blank")
        private String code;
    }
}
