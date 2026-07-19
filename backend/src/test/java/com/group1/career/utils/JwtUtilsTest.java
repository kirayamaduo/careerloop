package com.group1.career.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilsTest {

    /**
     * Phase 1 hardened JwtUtils to fail-fast unless {@code configure()} is
     * called with a 32-byte+ secret. The unit test exercises JwtUtils
     * directly (no Spring context, so JwtConfig never runs), so we have
     * to bootstrap it ourselves.
     */
    @BeforeAll
    static void configureJwt() {
        JwtUtils.configure(
                "test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret-12",
                3600_000L
        );
    }

    @Test
    @DisplayName("Generate token - returns non-null JWT string")
    public void testGenerateToken_ReturnsToken() {
        String token = JwtUtils.generateToken(1L, "USER");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // JWT format: three Base64 segments separated by dots
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Validate token - valid token returns true")
    public void testValidateToken_ValidToken() {
        String token = JwtUtils.generateToken(42L, "USER");
        assertTrue(JwtUtils.validateToken(token));
    }

    @Test
    @DisplayName("Validate token - tampered token returns false")
    public void testValidateToken_TamperedToken() {
        String token = JwtUtils.generateToken(1L, "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(JwtUtils.validateToken(tampered));
    }

    @Test
    @DisplayName("Validate token - null token returns false")
    public void testValidateToken_NullToken() {
        assertFalse(JwtUtils.validateToken(null));
    }

    @Test
    @DisplayName("Validate token - empty string returns false")
    public void testValidateToken_EmptyToken() {
        assertFalse(JwtUtils.validateToken(""));
    }

    @Test
    @DisplayName("Validate token - random garbage returns false")
    public void testValidateToken_GarbageToken() {
        assertFalse(JwtUtils.validateToken("not.a.jwt"));
    }

    @Test
    @DisplayName("Get userId from token - returns correct userId")
    public void testGetUserIdFromToken_ReturnsCorrectId() {
        Long userId = 99L;
        String token = JwtUtils.generateToken(userId, "USER");
        Long parsedId = JwtUtils.getUserIdFromToken(token);
        assertEquals(userId, parsedId);
    }

    @Test
    @DisplayName("Get userId from token - different users produce different tokens")
    public void testGenerateToken_DifferentUsersProduceDifferentTokens() {
        String token1 = JwtUtils.generateToken(1L, "USER");
        String token2 = JwtUtils.generateToken(2L, "USER");
        assertNotEquals(token1, token2);
        assertEquals(1L, JwtUtils.getUserIdFromToken(token1));
        assertEquals(2L, JwtUtils.getUserIdFromToken(token2));
    }

    @Test
    @DisplayName("Generate token - different roles produce different tokens")
    public void testGenerateToken_DifferentRolesProduceDifferentTokens() {
        String userToken = JwtUtils.generateToken(1L, "USER");
        String adminToken = JwtUtils.generateToken(1L, "ADMIN");
        assertNotEquals(userToken, adminToken);
    }

    @Test
    @DisplayName("Legacy tokens without authVersion remain generation zero")
    void legacyTokenDefaultsToGenerationZero() {
        String legacyToken = JwtUtils.generateToken(7L, "USER");

        assertEquals(0L, JwtUtils.getAuthVersionFromToken(legacyToken));
    }

    @Test
    @DisplayName("Revocable token carries its auth generation")
    void authVersionRoundTrips() {
        String token = JwtUtils.generateToken(7L, "USER", 12L);

        assertTrue(JwtUtils.validateToken(token));
        assertEquals(12L, JwtUtils.getAuthVersionFromToken(token));
    }
}
