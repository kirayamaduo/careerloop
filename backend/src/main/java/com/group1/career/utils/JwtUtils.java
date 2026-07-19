package com.group1.career.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * JWT helper. Configured at boot from application.yml/${JWT_SECRET} via JwtConfig.
 * <p>
 * There is intentionally NO compiled-in fallback secret: a missing or short
 * JWT_SECRET fails fast in JwtConfig, so production can never silently boot
 * with a developer key.
 */
@Slf4j
public class JwtUtils {

    /** 32 bytes = 256 bits = HS256 minimum. */
    private static final int MIN_SECRET_BYTES = 32;

    private static volatile Key KEY;
    private static volatile long EXPIRATION_TIME = 24 * 60 * 60 * 1000L;
    private static final String AUTH_VERSION_CLAIM = "authVersion";

    /**
     * Initialised by JwtConfig at Spring startup.
     *
     * @throws IllegalStateException if {@code secret} is null or shorter than 32 bytes.
     */
    public static void configure(String secret, long expirationMs) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret is missing or shorter than " + MIN_SECRET_BYTES + " bytes. " +
                    "Set the JWT_SECRET env var to a value generated with `openssl rand -hex 32` " +
                    "before starting the application."
            );
        }
        KEY = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        if (expirationMs > 0) {
            EXPIRATION_TIME = expirationMs;
        }
        log.info("JWT signing key configured ({} bytes, ttl={}ms)",
                secret.getBytes(StandardCharsets.UTF_8).length, EXPIRATION_TIME);
    }

    /**
     * Legacy token shape retained for rolling-deployment compatibility. Tokens
     * issued before auth-version support have no version claim and are treated
     * as generation zero by {@link #getAuthVersionFromToken(String)}.
     */
    public static String generateToken(Long userId, String role) {
        return tokenBuilder(userId, role).compact();
    }

    /** Generate a revocable token bound to the user's current auth generation. */
    public static String generateToken(Long userId, String role, long authVersion) {
        if (authVersion < 0) {
            throw new IllegalArgumentException("authVersion cannot be negative");
        }
        return tokenBuilder(userId, role)
                .claim(AUTH_VERSION_CLAIM, authVersion)
                .compact();
    }

    private static JwtBuilder tokenBuilder(Long userId, String role) {
        ensureConfigured();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(KEY, SignatureAlgorithm.HS256);
    }

    public static Long getUserIdFromToken(String token) {
        ensureConfigured();
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Return the token's server-side auth generation. A missing claim is zero
     * so JWTs issued immediately before this feature was deployed keep working
     * until the first password/reset/logout/deletion/ban event for that user.
     */
    public static long getAuthVersionFromToken(String token) {
        Object value = parseClaims(token).get(AUTH_VERSION_CLAIM);
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException("Invalid authVersion claim");
        }
    }

    public static boolean validateToken(String authToken) {
        ensureConfigured();
        try {
            Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(authToken);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token");
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty");
        } catch (JwtException e) {
            log.warn("Invalid JWT signature");
        }
        return false;
    }

    private static void ensureConfigured() {
        if (KEY == null) {
            throw new IllegalStateException(
                    "JWT not configured. Did Spring start? Did JwtConfig run? " +
                    "Make sure JWT_SECRET is set in the environment."
            );
        }
    }

    private static Claims parseClaims(String token) {
        ensureConfigured();
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
