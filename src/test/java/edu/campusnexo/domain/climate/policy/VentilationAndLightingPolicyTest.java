package edu.campusnexo.domain.climate.policy;

import static edu.campusnexo.support.TestData.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.climate.LightingDecision;
import edu.campusnexo.domain.climate.VentilationMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VentilationAndLightingPolicyTest {

    @Test
    @DisplayName("CO2 sobre el umbral activa ventilación mecánica")
    void co2ThresholdVentilation() {
        VentilationPolicy policy = new Co2ThresholdVentilationPolicy(1000, "prueba");
        assertEquals(VentilationMode.MECANICA, policy.recommend(context(20, 21, 14, 50, 300, 1200)).mode());
        assertEquals(VentilationMode.NINGUNA, policy.recommend(context(20, 21, 14, 50, 300, 800)).mode());
    }

    @Test
    @DisplayName("Templada: abre ventanas si el exterior es apto y usa ventiladores si no")
    void naturalVentilation() {
        VentilationPolicy policy = new NaturalVentilationPolicy(1000, 16, 27, 25);
        assertEquals(VentilationMode.NATURAL, policy.recommend(context(20, 23, 21, 50, 300, 1200)).mode());
        assertEquals(VentilationMode.MECANICA, policy.recommend(context(20, 23, 30, 50, 300, 1200)).mode());
        assertEquals(VentilationMode.NATURAL, policy.recommend(context(20, 27, 21, 50, 300, 700)).mode());
        assertEquals(VentilationMode.NINGUNA, policy.recommend(context(20, 23, 21, 50, 300, 700)).mode());
    }

    @Test
    @DisplayName("La iluminación complementa solo la luz natural que falta")
    void daylightHarvestingIsProportional() {
        LightingPolicy policy = new DaylightHarvestingPolicy(400);
        assertEquals(50, policy.recommend(context(10, 21, 14, 50, 200, 700)).targetLevelPct());
        assertEquals(0, policy.recommend(context(10, 21, 14, 50, 450, 700)).targetLevelPct());
        assertEquals(100, policy.recommend(context(10, 21, 14, 50, 0, 700)).targetLevelPct());
        assertEquals(0, policy.recommend(context(0, 21, 14, 50, 0, 700)).targetLevelPct());
    }

    @Test
    @DisplayName("Tropical: con deslumbramiento cierra persianas, incluso con el espacio vacío")
    void glareControlClosesBlinds() {
        LightingPolicy policy = new GlareControlLightingPolicy(400, 1500, 40);
        LightingDecision occupied = policy.recommend(context(10, 27, 32, 70, 2500, 700));
        assertTrue(occupied.closeBlinds());
        assertEquals(40, occupied.targetLevelPct());
        LightingDecision empty = policy.recommend(context(0, 27, 32, 70, 2500, 450));
        assertTrue(empty.closeBlinds());
        assertEquals(0, empty.targetLevelPct());
        assertFalse(policy.recommend(context(10, 27, 32, 70, 300, 700)).closeBlinds());
    }
}
