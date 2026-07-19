package com.group1.career.service.impl;

import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.AccountDeletionLog;
import com.group1.career.model.entity.User;
import com.group1.career.model.entity.UserAuth;
import com.group1.career.model.entity.UserRole;
import com.group1.career.repository.AccountDeletionLogRepository;
import com.group1.career.repository.UserAuthRepository;
import com.group1.career.repository.UserRepository;
import com.group1.career.repository.RoleRepository;
import com.group1.career.repository.UserRoleRepository;
import com.group1.career.service.FileService;
import com.group1.career.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;
    private final AccountDeletionLogRepository deletionLogRepository;

    private static final String EMAIL_PASSWORD = "EMAIL_PASSWORD";
    private static final String WECHAT = "WECHAT";
    private static final String DEFAULT_ROLE_CODE = "STUDENT";
    private static final int DELETION_GRACE_DAYS = 30;

    /** Avatars are loaded on every page that shows the user; keep the link alive a bit longer. */
    private static final long AVATAR_TTL_SECONDS = 30 * 60;

    @Value("${wechat.miniapp.appid}")
    private String wechatAppId;

    @Value("${wechat.miniapp.secret}")
    private String wechatSecret;

    @Override
    @Transactional
    public User register(String nickname, String identityType, String identifier, String credential) {
        validateIdentityType(identityType, EMAIL_PASSWORD);
        String normalizedIdentifier = normalizeIdentifier(identifier);

        Optional<UserAuth> existingAuth = userAuthRepository.findByIdentifierAndIdentityType(normalizedIdentifier, identityType);
        if (existingAuth.isPresent()) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = User.builder()
                .nickname(nickname)
                .build();
        user = userRepository.save(user);

        UserAuth userAuth = UserAuth.builder()
                .userId(user.getUserId())
                .identityType(identityType)
                .identifier(normalizedIdentifier)
                .credential(passwordEncoder.encode(credential))
                .build();
        userAuthRepository.save(userAuth);

        Long userId = user.getUserId();
        roleRepository.findByRoleCode(DEFAULT_ROLE_CODE).ifPresent(role -> {
            if (!userRoleRepository.existsByUserIdAndRoleId(userId, role.getRoleId())) {
                userRoleRepository.save(UserRole.builder()
                        .userId(userId)
                        .roleId(role.getRoleId())
                        .build());
            }
        });

        return user;
    }

    @Override
    @Transactional
    public User login(String identityType, String identifier, String credential) {
        validateIdentityType(identityType, EMAIL_PASSWORD);
        String normalizedIdentifier = normalizeIdentifier(identifier);

        UserAuth userAuth = userAuthRepository.findByIdentifierAndIdentityType(normalizedIdentifier, identityType)
                .orElseThrow(() -> new BizException(401, "邮箱或密码错误"));

        if (!passwordEncoder.matches(credential, userAuth.getCredential())) {
            // Keep the public response identical for unknown accounts and bad
            // passwords so this endpoint cannot be used for enumeration.
            throw new BizException(401, "邮箱或密码错误");
        }

        User user = getUserById(userAuth.getUserId());
        restoreAfterVerifiedLoginIfEligible(user);

        userAuth.setLastLoginTime(LocalDateTime.now());
        userAuthRepository.save(userAuth);

        return user;
    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    @Transactional
    public User updateUser(Long userId, String nickname, String avatarUrl,
                           String school, String major, Integer graduationYear,
                           boolean clearGraduationYear) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (nickname != null) user.setNickname(nickname);
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);
        if (school != null) user.setSchool(school);
        if (major != null) user.setMajor(major);
        if (clearGraduationYear) user.setGraduationYear(null);
        else if (graduationYear != null) user.setGraduationYear(graduationYear);

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User wechatLogin(String code) {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wechatAppId, wechatSecret, code);

        Map<String, Object> response = fetchWechatSession(url);

        if (response == null || (response.containsKey("errcode") && (Integer) response.get("errcode") != 0)) {
            // 40125/40013 mean the server-side appid/secret pair is wrong; the
            // user cannot fix that, so surface a friendly, actionable message
            // instead of letting the generic handler answer "System Error".
            log.error("WeChat login failed: {}", response);
            throw new BizException(503, "微信登录暂时不可用，请先使用邮箱注册登录，或稍后再试");
        }

        String openid = (String) response.get("openid");
        if (openid == null) {
            throw new BizException(503, "微信登录暂时不可用，请先使用邮箱注册登录，或稍后再试");
        }

        Optional<UserAuth> userAuthOpt = userAuthRepository.findByIdentifierAndIdentityType(openid, WECHAT);
        if (userAuthOpt.isPresent()) {
            UserAuth userAuth = userAuthOpt.get();
            User existing = getUserById(userAuth.getUserId());
            restoreAfterVerifiedLoginIfEligible(existing);
            userAuth.setLastLoginTime(LocalDateTime.now());
            userAuthRepository.save(userAuth);
            return existing;
        }

        User newUser = userRepository.save(User.builder().nickname("WeChat User").build());

        userAuthRepository.save(UserAuth.builder()
                .userId(newUser.getUserId())
                .identityType(WECHAT)
                .identifier(openid)
                .credential("")
                .lastLoginTime(LocalDateTime.now())
                .build());

        return newUser;
    }

    /**
     * Small seam for deterministic login/recovery tests; production still
     * performs the same jscode2session request.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> fetchWechatSession(String url) {
        // WeChat returns JSON with Content-Type: text/plain, so accept both.
        RestTemplate restTemplate = new RestTemplate();
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter =
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(java.util.List.of(
                org.springframework.http.MediaType.APPLICATION_JSON,
                org.springframework.http.MediaType.TEXT_PLAIN));
        restTemplate.getMessageConverters().add(0, converter);
        return restTemplate.getForObject(url, Map.class);
    }

    @Override
    public boolean isEmailRegistered(String email) {
        String normalized = normalizeIdentifier(email);
        return userAuthRepository.findByIdentifierAndIdentityType(normalized, EMAIL_PASSWORD).isPresent();
    }

    @Override
    @Transactional
    public void resetPassword(String identifier, String newCredential) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        UserAuth userAuth = userAuthRepository.findByIdentifierAndIdentityType(normalizedIdentifier, EMAIL_PASSWORD)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userAuth.setCredential(passwordEncoder.encode(newCredential));
        userAuthRepository.save(userAuth);
        revokeSessionsOrFail(userAuth.getUserId());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentCredential, String newCredential) {
        UserAuth userAuth = userAuthRepository.findByUserId(userId).stream()
                .filter(auth -> EMAIL_PASSWORD.equals(auth.getIdentityType()))
                .findFirst()
                .orElseThrow(() -> new BizException(400, "当前账号未设置邮箱密码"));
        if (!passwordEncoder.matches(currentCredential, userAuth.getCredential())) {
            throw new BizException(400, "当前密码错误");
        }
        userAuth.setCredential(passwordEncoder.encode(newCredential));
        userAuthRepository.save(userAuth);
        revokeSessionsOrFail(userId);
    }

    @Override
    @Transactional
    public void revokeSessions(Long userId) {
        revokeSessionsOrFail(userId);
    }

    private void validateIdentityType(String identityType, String expectedType) {
        if (!expectedType.equals(identityType)) {
            throw new RuntimeException("Unsupported identity type");
        }
    }

    private String normalizeIdentifier(String identifier) {
        return identifier == null ? null : identifier.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void requestDeletion(Long userId, String ipHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (user.getDeletedAt() != null) {
            return;
        }
        user.setDeletedAt(LocalDateTime.now());
        user.setAuthVersion(nextAuthVersion(user));
        userRepository.save(user);
        deletionLogRepository.save(AccountDeletionLog.builder()
                .userId(userId)
                .ipHash(ipHash)
                .build());
        log.info("[F25] One account requested deletion");
    }

    @Override
    @Transactional
    public void cancelDeletion(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (user.getDeletedAt() != null
                && !LocalDateTime.now().isBefore(
                        user.getDeletedAt().plusDays(DELETION_GRACE_DAYS))) {
            throw new BizException(ErrorCode.ACCOUNT_DELETED);
        }
        user.setDeletedAt(null);
        userRepository.save(user);
        markPendingDeletionCancelled(userId);
        log.info("[F25] One account cancelled a deletion request");
    }

    /**
     * Identity verification happens before this method. A verified login is
     * therefore the secure recovery proof for a still-pending deletion.
     * Banned accounts are checked first and can never use recovery to reactivate.
     */
    private void restoreAfterVerifiedLoginIfEligible(User user) {
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }
        if (user.getDeletedAt() == null) return;

        LocalDateTime deadline = user.getDeletedAt().plusDays(DELETION_GRACE_DAYS);
        if (!LocalDateTime.now().isBefore(deadline)) {
            throw new BizException(ErrorCode.ACCOUNT_DELETED);
        }

        user.setDeletedAt(null);
        user.setAccountRestored(true);
        userRepository.save(user);
        markPendingDeletionCancelled(user.getUserId());
        long remainingDays = Math.max(0L, ChronoUnit.DAYS.between(LocalDateTime.now(), deadline));
        log.info("[F25] Restored verified account during deletion grace period ({} days remaining)",
                remainingDays);
    }

    private void markPendingDeletionCancelled(Long userId) {
        deletionLogRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING")
                .ifPresent(logRow -> {
                    logRow.setStatus("CANCELLED");
                    logRow.setCancelledAt(LocalDateTime.now());
                    deletionLogRepository.save(logRow);
                });
    }

    private void revokeSessionsOrFail(Long userId) {
        if (userId == null || userRepository.incrementAuthVersion(userId) != 1) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private long nextAuthVersion(User user) {
        return (user.getAuthVersion() == null ? 0L : user.getAuthVersion()) + 1L;
    }

    @Override
    public User hydrateUrl(User user) {
        if (user == null) return null;
        String key = user.getAvatarUrl();
        if (key != null && !key.isBlank()) {
            user.setAvatarViewUrl(fileService.presignedUrl(key, AVATAR_TTL_SECONDS));
        }
        return user;
    }
}
