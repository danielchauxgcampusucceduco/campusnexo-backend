package edu.campusnexo.application.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorInvalidResponseException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.OccupancyEstimator;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OccupancyDecoratorsTest {
    private static final OccupancyQuery QUERY = new OccupancyQuery("AULA-301", 32, 48, null, 820, 20, 420);
    private static final OccupancyEstimate AI_RESULT =
            new OccupancyEstimate("AULA-301", 18, 82, false, true, OccupancySource.SERVICIO_IA, "ok");

    private static OccupancyEstimator failingTimes(int failures, AtomicInteger calls) {
        return query -> {
            if (calls.incrementAndGet() <= failures) {
                throw new OccupancyEstimatorUnavailableException(new RuntimeException("timeout"));
            }
            return AI_RESULT;
        };
    }

    @Test
    @DisplayName("Retry reintenta mientras el servicio no está disponible")
    void retryRecoversFromTransientFailure() {
        AtomicInteger calls = new AtomicInteger();
        OccupancyEstimate result = new RetryingOccupancyEstimator(failingTimes(1, calls), 2).estimate(QUERY);
        assertEquals(AI_RESULT, result);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("Retry no repite llamadas con respuestas inválidas")
    void retryDoesNotRepeatInvalidResponses() {
        AtomicInteger calls = new AtomicInteger();
        OccupancyEstimator invalid = query -> {
            calls.incrementAndGet();
            throw new OccupancyEstimatorInvalidResponseException("faltan campos", null);
        };
        assertThrows(OccupancyEstimatorInvalidResponseException.class,
                () -> new RetryingOccupancyEstimator(invalid, 3).estimate(QUERY));
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("Fallback responde con la heurística local cuando la IA falla, y lo indica en la fuente")
    void fallbackUsesLocalHeuristic() {
        AtomicInteger calls = new AtomicInteger();
        OccupancyEstimator decorated = new FallbackOccupancyEstimator(
                new RetryingOccupancyEstimator(failingTimes(5, calls), 2), new LocalHeuristicOccupancyEstimator());
        OccupancyEstimate result = decorated.estimate(QUERY);
        assertEquals(OccupancySource.RESPALDO_LOCAL, result.source());
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("La heurística local combina CO2 y Wi-Fi y reporta confianza baja")
    void localHeuristic() {
        OccupancyEstimate result = new LocalHeuristicOccupancyEstimator().estimate(QUERY);
        // Same fusion as the twin. CO2: (820 − 420)·48/1000 = 19.2, σ = 0.35·19.2 + 2 = 8.72;
        // Wi-Fi: 20·0.8 = 16, σ = 0.30·16 + 2 = 6.8. Inverse-variance mean = 17.2 → 17, σ = 5.36.
        assertEquals(17, result.occupancyEstimate());
        assertEquals(83.2, result.confidencePct(), 0.05, "1 − σ/capacidad = 1 − 5.36/32");
        assertEquals(OccupancySource.RESPALDO_LOCAL, result.source());
    }
}
