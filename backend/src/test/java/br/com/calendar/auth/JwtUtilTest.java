package br.com.calendar.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET = "dev-only-secret-change-in-production-0123456789abcdef";
    private static final String USER_ID = "usr_abc123";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 86400000L, 300000L, 86400000L);
    }

    @Test
    void generateTokenAndExtractUserId() {
        String token = jwtUtil.generateToken(USER_ID);

        assertEquals(USER_ID, jwtUtil.extractUserId(token));
    }

    @Test
    void tokenIsNotExpired() {
        String token = jwtUtil.generateToken(USER_ID);

        assertFalse(jwtUtil.isExpired(token));
    }

    @Test
    void expiredTokenIsDetected() {
        JwtUtil shortLivedJwt = new JwtUtil(SECRET, -1000L, -1000L, -1000L);
        String token = shortLivedJwt.generateToken(USER_ID);

        assertTrue(shortLivedJwt.isExpired(token));
    }

    @Test
    void accessTokenHasNoScope() {
        String token = jwtUtil.generateToken(USER_ID);

        assertNull(jwtUtil.getScope(token));
    }

    @Test
    void passwordResetTokenIsScoped() {
        String token = jwtUtil.generatePasswordResetToken(USER_ID);

        assertEquals(USER_ID, jwtUtil.extractUserId(token));
        assertEquals(JwtUtil.SCOPE_PASSWORD_RESET, jwtUtil.getScope(token));
    }

    @Test
    void emailConfirmationTokenIsScoped() {
        String token = jwtUtil.generateEmailConfirmationToken(USER_ID);

        assertEquals(USER_ID, jwtUtil.extractUserId(token));
        assertEquals(JwtUtil.SCOPE_EMAIL_CONFIRMATION, jwtUtil.getScope(token));
    }

    @Test
    void invalidTokenThrows() {
        assertThrows(Exception.class, () -> jwtUtil.extractUserId("invalid.token.here"));
    }
}