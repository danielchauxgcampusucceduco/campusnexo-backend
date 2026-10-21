package edu.campusnexo.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Allows {@code limit} calls per key in each fixed window; in memory, per instance. */
final class FixedWindowRateLimiter {
    private record Window(long start, int count) {
    }

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    boolean tryAcquire(String key) {
        if (limit <= 0) {
            return true;
        }
        long now = clock.millis();
        long start = now - now % windowMillis;
        Window updated = windows.compute(key, (ignored, current) ->
                current == null || current.start() != start ? new Window(start, 1) : new Window(start, current.count() + 1));
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().start() != start);
        }
        return updated.count() <= limit;
    }
}
