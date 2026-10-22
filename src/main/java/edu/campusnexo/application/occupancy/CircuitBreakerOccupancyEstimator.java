package edu.campusnexo.application.occupancy;

import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.port.OccupancyEstimator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decorator that stops calling a failing AI service. Its behaviour is a <b>State</b> pattern: the breaker
 * delegates every call to its current state object, and the states decide the transitions.
 *
 * <ul>
 *   <li>{@link Closed}: calls go through; after {@code failureThreshold} consecutive failures it opens.</li>
 *   <li>{@link Open}: calls fail immediately (the fallback answers in microseconds instead of waiting for
 *       timeouts) until {@code openDuration} or the service's {@code Retry-After} has passed.</li>
 *   <li>{@link HalfOpen}: a single trial call (concurrent callers fail fast); success closes the breaker,
 *       failure opens it again.</li>
 * </ul>
 * Only "unavailable" failures count: an invalid answer means the service is up but wrong.
 */
public class CircuitBreakerOccupancyEstimator implements OccupancyEstimator {
    private final OccupancyEstimator delegate;
    private final Clock clock;
    private final int failureThreshold;
    private final Duration openDuration;
    private final OperationalMetrics metrics;
    private BreakerState state = new Closed(0);

    public CircuitBreakerOccupancyEstimator(OccupancyEstimator delegate, Clock clock, int failureThreshold,
                                            Duration openDuration, OperationalMetrics metrics) {
        if (failureThreshold < 1 || openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("Parámetros del circuit breaker no válidos");
        }
        this.delegate = delegate;
        this.clock = clock;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.metrics = metrics;
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        return currentState().estimate(query);
    }

    /** Name of the current state, for health checks and tests. */
    public synchronized String stateName() {
        return state.name();
    }

    private synchronized BreakerState currentState() {
        return state;
    }

    private synchronized void moveTo(BreakerState next) {
        if (!next.name().equals(state.name())) {
            metrics.aiCircuitState(next.name());
        }
        state = next;
    }

    private Instant openUntil(OccupancyEstimatorUnavailableException failure) {
        Duration wait = failure.retryAfter().filter(retry -> retry.compareTo(openDuration) > 0).orElse(openDuration);
        return clock.instant().plus(wait);
    }

    private interface BreakerState {
        OccupancyEstimate estimate(OccupancyQuery query);

        String name();
    }

    private final class Closed implements BreakerState {
        private final int consecutiveFailures;

        Closed(int consecutiveFailures) {
            this.consecutiveFailures = consecutiveFailures;
        }

        @Override
        public OccupancyEstimate estimate(OccupancyQuery query) {
            try {
                OccupancyEstimate estimate = delegate.estimate(query);
                if (consecutiveFailures > 0) {
                    moveTo(new Closed(0));
                }
                return estimate;
            } catch (OccupancyEstimatorUnavailableException failure) {
                int failures = consecutiveFailures + 1;
                boolean asksToWait = failure.retryAfter().isPresent();
                moveTo(failures >= failureThreshold || asksToWait ? new Open(openUntil(failure)) : new Closed(failures));
                throw failure;
            }
        }

        @Override
        public String name() {
            return "CERRADO";
        }
    }

    private final class Open implements BreakerState {
        private final Instant until;

        Open(Instant until) {
            this.until = until;
        }

        @Override
        public OccupancyEstimate estimate(OccupancyQuery query) {
            if (clock.instant().isBefore(until)) {
                throw new OccupancyEstimatorUnavailableException(
                        "Circuito abierto: el servicio de IA falló repetidamente; se reintenta después de " + until,
                        null, null);
            }
            HalfOpen trial = new HalfOpen();
            moveTo(trial);
            return trial.estimate(query);
        }

        @Override
        public String name() {
            return "ABIERTO";
        }
    }

    private final class HalfOpen implements BreakerState {
        private final AtomicBoolean trialInFlight = new AtomicBoolean();

        @Override
        public OccupancyEstimate estimate(OccupancyQuery query) {
            if (!trialInFlight.compareAndSet(false, true)) {
                throw new OccupancyEstimatorUnavailableException(
                        "Circuito semiabierto: ya hay una llamada de prueba al servicio de IA en curso", null, null);
            }
            try {
                OccupancyEstimate estimate = delegate.estimate(query);
                moveTo(new Closed(0));
                return estimate;
            } catch (OccupancyEstimatorUnavailableException failure) {
                moveTo(new Open(openUntil(failure)));
                throw failure;
            }
        }

        @Override
        public String name() {
            return "SEMIABIERTO";
        }
    }
}
