package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnergyAccountingPolicyTest {
    private final EnergyAccountingPolicy policy = new EnergyAccountingPolicy(0.12, Duration.ofMinutes(15),
            LocalTime.of(7, 0), LocalTime.of(21, 0), ZoneId.of("America/Bogota"));
    /** 10:00 en Bogotá, dentro del horario. */
    private final Instant morning = Instant.parse("2026-10-20T15:00:00Z");

    @Test
    @DisplayName("En horario, un estado sin compresor ahorra toda la potencia nominal")
    void savesFullPowerWhenBlocked() {
        // 50 m² · 0.12 kW/m² = 6 kW durante 10 min = 1 kWh
        assertEquals(1.0, policy.savedKwh(HvacControlState.BLOQUEO_AHORRO_VACIO, morning,
                morning.plus(Duration.ofMinutes(10)), 50), 1e-9);
        assertEquals(0.0, policy.savedKwh(HvacControlState.CONFORT_ACTIVO, morning,
                morning.plus(Duration.ofMinutes(10)), 50), 1e-9);
    }

    @Test
    @DisplayName("Los huecos sin datos se limitan y fuera de horario encender cuenta como consumo extra")
    void capsGapsAndCountsOffScheduleUse() {
        assertEquals(1.5, policy.savedKwh(HvacControlState.APAGADO, morning, morning.plus(Duration.ofHours(3)), 50), 1e-9);
        Instant night = Instant.parse("2026-10-21T03:00:00Z");
        assertEquals(-1.0, policy.savedKwh(HvacControlState.CONFORT_ACTIVO, night, night.plus(Duration.ofMinutes(10)), 50), 1e-9);
    }
}
