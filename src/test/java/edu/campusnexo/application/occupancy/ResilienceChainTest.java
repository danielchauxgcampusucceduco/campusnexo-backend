package edu.campusnexo.application.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.RecordingMetrics;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The decorators in the order the composition root stacks them: fallback → breaker → retry → adapter. */
class ResilienceChainTest {
    private static final OccupancyQuery QUERY = new OccupancyQuery("AULA-301", 32, 48, 12, 820, 20, 420);

    @Test
    @DisplayName("La espera entre reintentos crece exponencialmente con jitter acotado")
    void exponentialBackoffWithJitter() {
        List<Duration> waits = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        OccupancyEstimator alwaysDown = query -> {
            calls.incrementAndGet();
            throw new OccupancyEstimatorUnavailableException(new RuntimeException("timeout"));
        };
        RetryingOccupancyEstimator retrying = new RetryingOccupancyEstimator(alwaysDown, 4, Duration.ofMillis(100),
                waits::add, () -> 1.0);

        assertThrows(OccupancyEstimatorUnavailableException.class, () -> retrying.estimate(QUERY));

        assertEquals(4, calls.get());
        assertEquals(List.of(Duration.ofMillis(150), Duration.ofMillis(300), Duration.ofMillis(600)), waits);
    }

    @Test
    @DisplayName("Si el servicio pidió esperar (Retry-After) no se reintenta")
    void doesNotRetryWhenAskedToWait() {
        AtomicInteger calls = new AtomicInteger();
        RetryingOccupancyEstimator retrying = new RetryingOccupancyEstimator(query -> {
            calls.incrementAndGet();
            throw new OccupancyEstimatorUnavailableException("cuota", null, Duration.ofSeconds(60));
        }, 3, Duration.ofMillis(100), duration -> { }, () -> 0);

        assertThrows(OccupancyEstimatorUnavailableException.class, () -> retrying.estimate(QUERY));
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("Con la IA caída la cadena completa responde con el respaldo local y lo cuenta")
    void fullChainFallsBackAndCountsIt() {
        RecordingMetrics metrics = new RecordingMetrics();
        AtomicInteger calls = new AtomicInteger();
        OccupancyEstimator adapter = query -> {
            calls.incrementAndGet();
            throw new OccupancyEstimatorUnavailableException(new RuntimeException("connection refused"));
        };
        OccupancyEstimator chain = new FallbackOccupancyEstimator(new CircuitBreakerOccupancyEstimator(
                new RetryingOccupancyEstimator(adapter, 2), new MutableClock(TestData.NOW), 3, Duration.ofSeconds(30),
                metrics), new LocalHeuristicOccupancyEstimator(), metrics);

        for (int i = 0; i < 5; i++) {
            OccupancyEstimate estimate = chain.estimate(QUERY);
            assertEquals(OccupancySource.RESPALDO_LOCAL, estimate.source());
        }

        assertEquals(6, calls.get(), "3 evaluaciones × 2 intentos; luego el circuito abierto ya no llama");
        assertEquals(5, metrics.fallbacks.size());
    }
}
