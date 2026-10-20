package edu.campusnexo.application.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmptySpaceEnergyEstimatorTest {

    @Test
    @DisplayName("Integra la potencia de las lecturas vacías y limita los huecos sin datos")
    void integratesEmptyReadings() {
        Instant t0 = TestData.NOW.minus(Duration.ofHours(2));
        EmptySpaceEnergyEstimator estimator = new EmptySpaceEnergyEstimator(Duration.ofMinutes(15));
        EmptySpaceEnergyEstimator.Result result = estimator.estimate(List.of(
                TestData.reading("AULA-301", t0, 0, 450, 2.0),                         // 10 min vacío -> 0.333 kWh
                TestData.reading("AULA-301", t0.plus(Duration.ofMinutes(10)), 12, 700, 2.0), // ocupado: no cuenta
                TestData.reading("AULA-301", t0.plus(Duration.ofMinutes(20)), 0, 450, 1.0)   // hasta ahora, tope 15 min -> 0.25
        ), TestData.NOW, spaceId -> 1000.0);
        assertEquals(0.583, result.kwh());
        assertEquals(583.0, result.costCop());
    }
}
