package br.com.calendar.auth;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Simple fixed-window rate limiter.
//
// WARNING: this is in-memory, same caveat as TokenBlacklist. If more than
// one backend instance is running (load balancer, etc.), each instance
// tracks attempts independently, so the effective limit is
// (instances x MAX_ATTEMPTS). Switch to Redis/DB when multi-instance
// support is needed.
@Component
@EnableScheduling
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Window(AtomicInteger attempts, Instant resetAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    // Read-only: has this key already hit MAX_ATTEMPTS in the current
    // window? Callers check this before doing any work, and separately call
    // recordAttempt() themselves — this lets a caller choose to only count
    // failures (e.g. login) rather than every call.
    public boolean isBlocked(String key) {
        Window window = windows.get(key);
        return window != null
                && window.resetAt().isAfter(Instant.now())
                && window.attempts().get() >= MAX_ATTEMPTS;
    }

    // Records one attempt against the key, starting a new window if the
    // previous one (if any) has expired.
    public void recordAttempt(String key) {
        Instant now = Instant.now();
        Window window = windows.compute(key, (k, existing) ->
                (existing == null || existing.resetAt().isBefore(now))
                        ? new Window(new AtomicInteger(0), now.plus(WINDOW))
                        : existing);

        window.attempts().incrementAndGet();
    }

    // Runs hourly to clear expired windows from memory.
    @Scheduled(fixedRate = 3600000)
    public void cleanExpired() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> entry.getValue().resetAt().isBefore(now));
    }
}
