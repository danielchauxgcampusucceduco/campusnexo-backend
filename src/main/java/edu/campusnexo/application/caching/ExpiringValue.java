package edu.campusnexo.application.caching;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/** A value loaded on first use and reloaded once it is older than its time to live. Thread-safe. */
final class ExpiringValue<T> {
    private final Clock clock;
    private final Duration ttl;
    private final Supplier<T> loader;
    private T value;
    private Instant loadedAt;

    ExpiringValue(Clock clock, Duration ttl, Supplier<T> loader) {
        if (ttl == null || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl no válido");
        }
        this.clock = clock;
        this.ttl = ttl;
        this.loader = loader;
    }

    synchronized T get() {
        Instant now = clock.instant();
        if (value == null || !now.isBefore(loadedAt.plus(ttl))) {
            value = loader.get();
            loadedAt = now;
        }
        return value;
    }

    synchronized void invalidate() {
        value = null;
    }
}
