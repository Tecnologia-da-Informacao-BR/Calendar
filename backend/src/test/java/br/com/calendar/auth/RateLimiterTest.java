package br.com.calendar.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void newKeyIsNotBlocked() {
        assertFalse(rateLimiter.isBlocked("key-fresh"));
    }

    @Test
    void fiveAttemptsAreAllowedThroughBeforeTheSixthIsBlocked() {
        String key = "key-five-then-blocked";

        for (int i = 0; i < 5; i++) {
            assertFalse(rateLimiter.isBlocked(key));
            rateLimiter.recordAttempt(key);
        }

        assertTrue(rateLimiter.isBlocked(key));
    }

    @Test
    void tracksDifferentKeysIndependently() {
        String keyA = "key-a";
        String keyB = "key-b";

        for (int i = 0; i < 5; i++) {
            rateLimiter.recordAttempt(keyA);
        }

        assertTrue(rateLimiter.isBlocked(keyA));
        assertFalse(rateLimiter.isBlocked(keyB));
    }

    @Test
    void isBlockedDoesNotItselfCountAsAnAttempt() {
        String key = "key-read-only-check";

        for (int i = 0; i < 20; i++) {
            rateLimiter.isBlocked(key);
        }

        assertFalse(rateLimiter.isBlocked(key));
    }
}
