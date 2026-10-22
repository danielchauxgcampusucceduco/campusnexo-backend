package edu.campusnexo.application.occupancy;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.port.OccupancyEstimator;
import java.time.Duration;
import java.util.function.DoubleSupplier;

/**
 * Decorator: retries the wrapped estimator when it is unavailable (timeouts, connection errors), waiting
 * {@code base × 2^(attempt−1)} plus up to 50 % random jitter so many clients do not retry in lockstep. A
 * service that asked us to wait ({@code Retry-After}) is not retried: the circuit breaker honours the wait.
 * Invalid responses are not retried either, because repeating the call would return the same data.
 */
public class RetryingOccupancyEstimator implements OccupancyEstimator {
    private final OccupancyEstimator delegate;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;

    public RetryingOccupancyEstimator(OccupancyEstimator delegate, int maxAttempts, Duration baseBackoff,
                                      Sleeper sleeper, DoubleSupplier jitter) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
        if (baseBackoff == null || baseBackoff.isNegative()) {
            throw new IllegalArgumentException("baseBackoff no puede ser negativo");
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    /** Retries without waiting (tests and tools). */
    public RetryingOccupancyEstimator(OccupancyEstimator delegate, int maxAttempts) {
        this(delegate, maxAttempts, Duration.ZERO, duration -> { }, () -> 0);
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.estimate(query);
            } catch (OccupancyEstimatorUnavailableException failure) {
                if (attempt >= maxAttempts || failure.retryAfter().isPresent()) {
                    throw failure;
                }
                sleeper.sleep(backoff(attempt));
            }
        }
    }

    Duration backoff(int attempt) {
        long base = baseBackoff.toMillis() << (attempt - 1);
        return Duration.ofMillis(base + Math.round(base * 0.5 * jitter.getAsDouble()));
    }
}
