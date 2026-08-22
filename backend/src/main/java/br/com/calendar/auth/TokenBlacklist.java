package br.com.calendar.auth;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@EnableScheduling
public class TokenBlacklist {

    // WARNING: this is in-memory. If more than one backend instance is
    // running (load balancer, etc.), a logout on one instance won't revoke
    // the token on the others. Switch to Redis/DB when multi-instance
    // support is needed.
    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();
    private final JwtUtil jwtUtil;

    public TokenBlacklist(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public void revoke(String token) {
        Date expiration = jwtUtil.getExpirationDate(token);
        revokedTokens.put(token, expiration.toInstant());
    }

    public boolean isRevoked(String token) {
        return revokedTokens.containsKey(token);
    }

    // Runs hourly to clear expired tokens from memory, without relying on
    // someone calling logout to trigger the cleanup.
    @Scheduled(fixedRate = 3600000)
    public void cleanExpired() {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}