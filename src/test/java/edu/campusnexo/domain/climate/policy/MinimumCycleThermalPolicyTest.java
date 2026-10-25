package edu.campusnexo.domain.climate.policy;

import static edu.campusnexo.support.TestData.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MinimumCycleThermalPolicyTest {
    private final ThermalPolicy policy = new MinimumCycleThermalPolicy(new HeatingFirstThermalPolicy(20, 25, 1), 10, 5);

    @Test
    @DisplayName("No apaga el compresor antes del tiempo mínimo encendido")
    void keepsRunningBeforeMinimumOnTime() {
        HvacDecision decision = policy.recommend(context(0, 21, HvacMode.CALEFACCION, 4));
        assertEquals(HvacMode.CALEFACCION, decision.mode());
        assertTrue(decision.explanation().contains("tiempo mínimo encendido"));
    }

    @Test
    @DisplayName("No enciende el compresor antes del tiempo mínimo de reposo")
    void staysOffBeforeMinimumOffTime() {
        HvacDecision decision = policy.recommend(context(20, 15, HvacMode.APAGADO, 2));
        assertEquals(HvacMode.APAGADO, decision.mode());
        assertTrue(decision.explanation().contains("reposo"));
    }

    @Test
    @DisplayName("Cumplidos los tiempos, delega la decisión al algoritmo envuelto")
    void delegatesWhenCycleAllows() {
        assertEquals(HvacMode.CALEFACCION, policy.recommend(context(20, 15, HvacMode.APAGADO, 30)).mode());
        assertEquals(HvacMode.APAGADO, policy.recommend(context(0, 21, HvacMode.CALEFACCION, 30)).mode());
    }
}
