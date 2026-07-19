package com.group1.career.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${alert.email:}")
    private String alertEmail;

    public void sendVerificationCode(String toEmail, String code, String purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);

            String subject;
            String htmlContent;

            if ("REGISTER".equals(purpose)) {
                subject = "【Career Loop】邮箱验证码";
                htmlContent = buildVerificationEmail(code, "注册账号", 5);
            } else {
                subject = "【Career Loop】密码重置验证码";
                htmlContent = buildVerificationEmail(code, "重置密码", 5);
            }

            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Verification email sent to: {}", maskEmail(toEmail));
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", maskEmail(toEmail), e.getMessage());
            throw new RuntimeException("Failed to send verification email");
        }
    }

    /** F26: Notify admin email when a new user feedback is submitted. Best-effort. */
    @Async
    public void sendFeedbackAlert(Long feedbackId, String category, String userIdStr, String content, String contact) {
        if (alertEmail == null || alertEmail.isBlank()) {
            log.debug("[feedback] alert email is not configured; skipping feedback#{}", feedbackId);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(alertEmail);
            helper.setSubject("【CareerLoop 用户反馈】" + category + " #" + feedbackId);
            String html = """
                    <div style="font-family:-apple-system,sans-serif;max-width:560px;margin:0 auto;padding:28px 20px;">
                      <h2 style="color:#2457d6;margin-bottom:4px;">新用户反馈 #%d</h2>
                      <p style="color:#64748b;font-size:13px;margin-bottom:18px;">类型：<strong>%s</strong>&nbsp;·&nbsp;用户：%s</p>
                      <div style="background:#f8fafc;border-left:4px solid #2563eb;padding:14px 16px;border-radius:4px;margin-bottom:16px;">
                        <p style="margin:0;color:#1e293b;font-size:15px;white-space:pre-wrap;">%s</p>
                      </div>
                      %s
                    </div>
                    """.formatted(
                    feedbackId, escape(category),
                    userIdStr != null ? "user#" + escape(userIdStr) : "匿名",
                    escape(content),
                    contact != null && !contact.isBlank()
                            ? "<p style='color:#475569;font-size:13px;'>联系方式：" + escape(contact) + "</p>"
                            : "");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[feedback] alert email sent for feedback#{}", feedbackId);
        } catch (Exception e) {
            log.warn("[feedback] failed to send alert email: {}", e.getMessage());
        }
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String buildVerificationEmail(String code, String action, int expireMinutes) {
        return """
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Helvetica Neue',Arial,sans-serif;
                            max-width:480px;margin:0 auto;padding:32px 24px;background:#ffffff;">
                  <div style="text-align:center;margin-bottom:24px;">
                    <span style="font-size:22px;font-weight:800;color:#2457d6;letter-spacing:1px;">CAREER LOOP</span>
                  </div>
                  <h2 style="font-size:18px;font-weight:700;color:#1e293b;margin-bottom:8px;">验证码 — %s</h2>
                  <p style="color:#64748b;font-size:14px;margin-bottom:24px;">
                    您正在 Career Loop 进行 <strong>%s</strong>，请使用以下验证码完成操作：
                  </p>
                  <div style="background:#eef4ff;border-radius:14px;padding:20px;text-align:center;margin-bottom:24px;">
                    <span style="font-size:36px;font-weight:800;letter-spacing:10px;color:#2457d6;">%s</span>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;margin:0;">
                    验证码 <strong>%d 分钟</strong>内有效，请勿泄露给他人。<br/>
                    如非本人操作，请忽略此邮件。
                  </p>
                </div>
                """.formatted(action, action, code, expireMinutes);
    }
}
