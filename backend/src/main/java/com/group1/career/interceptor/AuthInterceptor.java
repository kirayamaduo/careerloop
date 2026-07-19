package com.group1.career.interceptor;

import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.User;
import com.group1.career.repository.UserRepository;
import com.group1.career.utils.JwtUtils;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || !JwtUtils.validateToken(token)) {
            throw new BizException(ErrorCode.UNAUTHORIZED_ERROR);
        }

        final Long userId;
        final long tokenAuthVersion;
        try {
            userId = JwtUtils.getUserIdFromToken(token);
            tokenAuthVersion = JwtUtils.getAuthVersionFromToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BizException(ErrorCode.UNAUTHORIZED_ERROR);
        }

        // F25: reject soft-deleted accounts (410 Gone)
        // F16: reject banned accounts (403 Forbidden)
        // Exception: cancel-deletion endpoint must be accessible even after deleted_at is set
        //            so the user can recover their account within the 30-day grace period.
        boolean isCancelDeletion = request.getRequestURI().endsWith("/users/me/cancel-deletion");
        // A cryptographically valid token is not itself proof that its subject
        // still exists. Fail closed after permanent erasure instead of letting
        // a stale token act as a "ghost" user and create orphaned rows.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED_ERROR));
        if (!isCancelDeletion && user.getDeletedAt() != null) {
            throw new BizException(ErrorCode.ACCOUNT_DELETED);
        }
        // The admin API stores BANNED as 2. Treat every explicit non-active
        // value as disabled so a future status cannot accidentally fail open.
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }
        long currentAuthVersion = user.getAuthVersion() == null ? 0L : user.getAuthVersion();
        if (tokenAuthVersion != currentAuthVersion) {
            throw new BizException(ErrorCode.UNAUTHORIZED_ERROR);
        }

        request.setAttribute("userId", userId);
        return true;
    }
}
