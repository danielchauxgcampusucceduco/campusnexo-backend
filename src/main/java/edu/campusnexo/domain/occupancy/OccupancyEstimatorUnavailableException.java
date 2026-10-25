package edu.campusnexo.domain.occupancy;

import java.time.Duration;
import java.util.Optional;

/**
 * The estimator could not be reached, did not answer in time, is rate limiting us or its circuit is open.
 * {@link #retryAfter()} is the wait the service asked for (HTTP 429 or 503 with {@code Retry-After}), if any.
 */
public final class OccupancyEstimatorUnavailableException extends OccupancyEstimationFailedException {
    private final transient Duration retryAfter;

    public OccupancyEstimatorUnavailableException(Throwable cause) {
        this("El servicio de IA no está disponible; inténtalo de nuevo más tarde.", cause, null);
    }

    public OccupancyEstimatorUnavailableException(String message, Throwable cause, Duration retryAfter) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
