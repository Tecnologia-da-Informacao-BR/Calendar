package br.com.calendar.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistTest {

    @Mock
    private JwtUtil jwtUtil;

    private TokenBlacklist tokenBlacklist;

    @BeforeEach
    void setUp() {
        tokenBlacklist = new TokenBlacklist(jwtUtil);
    }

    @Test
    void revokedTokenIsBlacklisted() {
        String token = "abc123";
        when(jwtUtil.getExpirationDate(token)).thenReturn(Date.from(java.time.Instant.now().plusSeconds(3600)));

        tokenBlacklist.revoke(token);

        assertTrue(tokenBlacklist.isRevoked(token));
    }

    @Test
    void tokenNotRevokedIsNotBlacklisted() {
        String token = "other-token";
        when(jwtUtil.getExpirationDate(token)).thenReturn(Date.from(java.time.Instant.now().plusSeconds(3600)));

        tokenBlacklist.revoke(token);

        assertFalse(tokenBlacklist.isRevoked("never-revoked"));
    }

    @Test
    void cleanExpiredRemovesOnlyExpiredTokens() {
        String expired = "expired-token";
        String valid = "valid-token";

        when(jwtUtil.getExpirationDate(expired))
                .thenReturn(Date.from(java.time.Instant.now().minusSeconds(3600)));
        when(jwtUtil.getExpirationDate(valid))
                .thenReturn(Date.from(java.time.Instant.now().plusSeconds(3600)));

        tokenBlacklist.revoke(expired);
        tokenBlacklist.revoke(valid);

        tokenBlacklist.cleanExpired();

        assertFalse(tokenBlacklist.isRevoked(expired));
        assertTrue(tokenBlacklist.isRevoked(valid));
    }
}