package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private final Instant tenMinutes = morning.plus(Duration.ofMinutes(10));

    @Test
    @DisplayName("En uso programado, un estado sin compresor ahorra toda la potencia nominal y el confort nada")
    void savesFullPowerWhenBlockedDuringScheduledUse() {
        // 50 m² · 0.12 kW/m² = 6 kW durante 10 min = 1 kWh
        assertEquals(1.0, policy.savedKwh(HvacControlState.CORTE_INERCIA_TERMICA, true, morning, tenMinutes, 50), 1e-9);
        assertEquals(0.75, policy.savedKwh(HvacControlState.VENTILACION_CO2_ACTIVA, true, morning, tenMinutes, 50), 1e-9);
        assertEquals(0.0, policy.savedKwh(HvacControlState.CONFORT_ACTIVO, true, morning, tenMinutes, 50), 1e-9);
    }

    @Test
    @DisplayName("Un salón apagado y sin uso programado no aporta consumo ni ahorro: nunca cuenta en contra")
    void anIdleRoomIsZero() {
        assertEquals(0.0, policy.savedKwh(HvacControlState.BLOQUEO_AHORRO_VACIO, false, morning, tenMinutes, 50), 1e-9);
        assertEquals(0.0, policy.savedKwh(HvacControlState.APAGADO, false, morning, tenMinutes, 50), 1e-9);
        for (HvacControlState state : HvacControlState.values()) {
            if (state.powerFraction() == 0) {
                assertTrue(policy.savedKwh(state, false, morning, tenMinutes, 50) >= 0, state + " apagado no resta");
            }
        }
    }

    @Test
    @DisplayName("Los huecos sin datos se limitan; climatizar fuera de uso programado o de horario sí cuenta como extra")
    void capsGapsAndCountsUnscheduledUse() {
        assertEquals(1.5, policy.savedKwh(HvacControlState.APAGADO, true, morning, morning.plus(Duration.ofHours(3)), 50), 1e-9);
        assertEquals(-1.0, policy.savedKwh(HvacControlState.PRE_CALENTAMIENTO_IA, false, morning, tenMinutes, 50), 1e-9);
        Instant night = Instant.parse("2026-10-21T03:00:00Z");
        assertEquals(-1.0, policy.savedKwh(HvacControlState.CONFORT_ACTIVO, true, night, night.plus(Duration.ofMinutes(10)), 50), 1e-9);
    }
}
