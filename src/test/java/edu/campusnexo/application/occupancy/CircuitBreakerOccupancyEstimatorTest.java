package edu.campusnexo.application.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorInvalidResponseException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.RecordingMetrics;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircuitBreakerOccupancyEstimatorTest {
    private static final OccupancyQuery QUERY = new OccupancyQuery("AULA-301", 32, 48, 12, 820, 20, 420);
    private static final OccupancyEstimate OK =
            new OccupancyEstimate("AULA-301", 12, 90, true, true, OccupancySource.SERVICIO_IA, "ok");

    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final RecordingMetrics metrics = new RecordingMetrics();
    private final AtomicBoolean down = new AtomicBoolean(true);
    private final AtomicInteger calls = new AtomicInteger();
    private final OccupancyEstimator service = query -> {
        calls.incrementAndGet();
        if (down.get()) {
            throw new OccupancyEstimatorUnavailableException(new RuntimeException("timeout"));
        }
        return OK;
    };
    private final CircuitBreakerOccupancyEstimator breaker =
            new CircuitBreakerOccupancyEstimator(service, clock, 3, Duration.ofSeconds(30), metrics);

    private void failOnce() {
        assertThrows(OccupancyEstimatorUnavailableException.class, () -> breaker.estimate(QUERY));
    }

    @Test
    @DisplayName("Tras 3 fallas seguidas se abre y deja de llamar al servicio")
    void opensAfterConsecutiveFailures() {
        failOnce();
        failOnce();
        assertEquals("CERRADO", breaker.stateName());
        failOnce();
        assertEquals("ABIERTO", breaker.stateName());

        failOnce();
        failOnce();
        assertEquals(3, calls.get(), "abierto: responde sin esperar timeouts");
        assertEquals(List.of("ABIERTO"), metrics.circuitStates);
    }

    @Test
    @DisplayName("Pasado el tiempo abierto prueba una llamada: si funciona, se cierra")
    void halfOpenTrialClosesOnSuccess() {
        failOnce();
        failOnce();
        failOnce();
        clock.advance(Duration.ofSeconds(31));
        down.set(false);

        assertEquals(OK, breaker.estimate(QUERY));
        assertEquals("CERRADO", breaker.stateName());
        assertEquals(List.of("ABIERTO", "SEMIABIERTO", "CERRADO"), metrics.circuitStates);
    }

    @Test
    @DisplayName("Si la llamada de prueba falla, vuelve a abrirse")
    void halfOpenTrialReopensOnFailure() {
        failOnce();
        failOnce();
        failOnce();
        clock.advance(Duration.ofSeconds(31));
        failOnce();
        assertEquals("ABIERTO", breaker.stateName());
        assertEquals(4, calls.get());
    }

    @Test
    @DisplayName("Un éxito reinicia el conteo de fallas")
    void successResetsTheFailureCount() {
        failOnce();
        failOnce();
        down.set(false);
        breaker.estimate(QUERY);
        down.set(true);
        failOnce();
        failOnce();
        assertEquals("CERRADO", breaker.stateName());
    }

    @Test
    @DisplayName("Un 429 con Retry-After abre de inmediato y respeta la espera pedida")
    void retryAfterOpensForTheRequestedTime() {
        CircuitBreakerOccupancyEstimator limited = new CircuitBreakerOccupancyEstimator(query -> {
            calls.incrementAndGet();
            throw new OccupancyEstimatorUnavailableException("cuota", null, Duration.ofMinutes(2));
        }, clock, 3, Duration.ofSeconds(30), metrics);

        assertThrows(OccupancyEstimatorUnavailableException.class, () -> limited.estimate(QUERY));
        assertEquals("ABIERTO", limited.stateName());
        clock.advance(Duration.ofSeconds(90));
        assertThrows(OccupancyEstimatorUnavailableException.class, () -> limited.estimate(QUERY));
        assertEquals(1, calls.get(), "a los 90 s todavía no se vuelve a llamar");
    }

    @Test
    @DisplayName("Una respuesta inválida no abre el circuito: el servicio está arriba")
    void invalidAnswersDoNotCount() {
        CircuitBreakerOccupancyEstimator wrong = new CircuitBreakerOccupancyEstimator(query -> {
            throw new OccupancyEstimatorInvalidResponseException("campo faltante", null);
        }, clock, 1, Duration.ofSeconds(30), metrics);

        assertThrows(OccupancyEstimatorInvalidResponseException.class, () -> wrong.estimate(QUERY));
        assertEquals("CERRADO", wrong.stateName());
    }

    @Test
    @DisplayName("Rechaza parámetros sin sentido")
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new CircuitBreakerOccupancyEstimator(service, clock, 0, Duration.ofSeconds(30), metrics));
        assertThrows(IllegalArgumentException.class,
                () -> new CircuitBreakerOccupancyEstimator(service, clock, 3, Duration.ZERO, metrics));
    }
}
