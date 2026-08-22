package br.com.calendar.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    public static final String SCOPE_PASSWORD_RESET = "password_reset";
    public static final String SCOPE_EMAIL_CONFIRMATION = "email_confirmation";

    private static final String SCOPE_CLAIM = "scope";

    private final SecretKey key;
    @Getter
    private final long expirationMs;
    @Getter
    private final long resetExpirationMs;
    @Getter
    private final long emailConfirmationExpirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms}") long expirationMs,
                   @Value("${jwt.reset-expiration-ms}") long resetExpirationMs,
                   @Value("${jwt.email-confirmation-expiration-ms}") long emailConfirmationExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.resetExpirationMs = resetExpirationMs;
        this.emailConfirmationExpirationMs = emailConfirmationExpirationMs;
    }

    public String generateToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String generatePasswordResetToken(String userId) {
        return generateScopedToken(userId, SCOPE_PASSWORD_RESET, resetExpirationMs);
    }

    public String generateEmailConfirmationToken(String userId) {
        return generateScopedToken(userId, SCOPE_EMAIL_CONFIRMATION, emailConfirmationExpirationMs);
    }

    private String generateScopedToken(String userId, String scope, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(SCOPE_CLAIM, scope)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String getScope(String token) {
        return parseClaims(token).get(SCOPE_CLAIM, String.class);
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public Date getExpirationDate(String token) {
        return parseClaims(token).getExpiration();
    }

    public boolean isExpired(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}