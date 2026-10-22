package edu.campusnexo.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {
    @Test
    void permitsOnlyTheConfiguredCallsAndResetsAtTheNextFixedWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-21T12:00:00Z"));
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, Duration.ofMinutes(1), clock);

        assertTrue(limiter.tryAcquire("daniel"));
        assertTrue(limiter.tryAcquire("daniel"));
        assertFalse(limiter.tryAcquire("daniel"));
        assertTrue(limiter.tryAcquire("jurado"));
        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("daniel"));
    }

    @Test
    void rejectsDurationsThatWouldProduceAnInvalidWindowDivisor() {
        MutableClock clock = new MutableClock(Instant.EPOCH);

        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(5, Duration.ZERO, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(5, Duration.ofNanos(1), clock));
    }
}
