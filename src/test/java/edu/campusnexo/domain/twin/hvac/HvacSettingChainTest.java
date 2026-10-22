package edu.campusnexo.domain.twin.hvac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.climate.policy.ClimateFamilies;
import edu.campusnexo.domain.climate.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.HvacSetting;
import edu.campusnexo.domain.twin.ThermalDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HvacSettingChainTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final ClimatePolicyFactory ANDEAN = ClimateFamilies.standard().of(ClimateProfile.SEDE_ANDINA);
    private static final ClimatePolicyFactory TROPICAL = ClimateFamilies.standard().of(ClimateProfile.SEDE_TROPICAL);
    private final HvacSettingChain chain = HvacSettingChain.standard();

    private static ThermalDecision predicted(HvacControlState state, HvacMode mode, double setpoint) {
        return new ThermalDecision(state, mode, setpoint, 30, 5, List.of(21.0), "MPC: " + state);
    }

    private static HvacSetting running(HvacControlState state, HvacMode mode, Duration since) {
        return new HvacSetting(state, mode, 21, NOW.minus(since), "antes");
    }

    private static ClimateContext room(int people, double indoorC, double humidity) {
        return new ClimateContext(people, 700, indoorC, 14, humidity, 300, new HvacState(HvacMode.APAGADO, 60));
    }

    private static HvacRequest request(boolean hvac, boolean maintenance, boolean override, HvacSetting previous,
                                       ThermalDecision predicted, ClimatePolicyFactory family, ClimateContext climate) {
        return new HvacRequest(hvac, maintenance, override, previous, predicted, family, climate, false, NOW);
    }

    private static HvacSetting idleForLong() {
        return running(HvacControlState.BLOQUEO_AHORRO_VACIO, HvacMode.APAGADO, Duration.ofHours(1));
    }

    @Test
    @DisplayName("El orden de la cadena expresa la precedencia de las reglas")
    void rulesAreOrderedByPrecedence() {
        assertEquals(List.of("NoClimateUnitRule", "MaintenanceRule", "ManualOverrideRule", "CompressorProtectionRule",
                "FamilyStrategyRule", "PredictiveControlRule"), chain.ruleNames());
    }

    @Test
    @DisplayName("Un salón sin equipo nunca se climatiza")
    void roomWithoutUnit() {
        HvacResolution resolution = chain.proceed(request(false, false, false, idleForLong(),
                predicted(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, 20), ANDEAN, room(20, 17, 50)));
        assertEquals(HvacControlState.SIN_CLIMATIZACION, resolution.state());
        assertEquals("NoClimateUnitRule", resolution.decidedBy());
    }

    @Test
    @DisplayName("El mantenimiento apaga de inmediato, aunque el compresor lleve un minuto encendido")
    void maintenanceStopsImmediately() {
        HvacSetting justStarted = running(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, Duration.ofMinutes(1));
        HvacResolution resolution = chain.proceed(request(true, true, false, justStarted,
                predicted(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, 20), ANDEAN, room(20, 17, 50)));
        assertEquals(HvacControlState.APAGADO, resolution.state());
        assertEquals("MaintenanceRule", resolution.decidedBy());
    }

    @Test
    @DisplayName("El control del docente lleva el salón al centro de la banda de su familia")
    void teacherOverride() {
        HvacResolution resolution = chain.proceed(request(true, false, true, idleForLong(),
                predicted(HvacControlState.CORTE_INERCIA_TERMICA, HvacMode.APAGADO, 22), ANDEAN, room(20, 18, 50)));
        assertEquals(HvacControlState.OVERRIDE_MANUAL_DOCENTE, resolution.state());
        assertEquals(HvacMode.CALEFACCION, resolution.mode());
        assertEquals(21.5, resolution.setpointC());
    }

    @Test
    @DisplayName("No apaga un compresor que lleva menos del mínimo encendido")
    void holdsTheMinimumOnTime() {
        HvacSetting heating = running(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, Duration.ofMinutes(4));
        HvacResolution resolution = chain.proceed(request(true, false, false, heating,
                predicted(HvacControlState.CORTE_INERCIA_TERMICA, HvacMode.APAGADO, 22), ANDEAN, room(20, 21, 50)));
        assertEquals(HvacControlState.CONFORT_ACTIVO, resolution.state());
        assertEquals(HvacMode.CALEFACCION, resolution.mode());
        assertEquals("CompressorProtectionRule", resolution.decidedBy());
        assertTrue(resolution.explanation().contains("mínimo 10 min"));
    }

    @Test
    @DisplayName("No enciende un compresor que no ha cumplido el reposo mínimo")
    void holdsTheMinimumOffTime() {
        HvacSetting stopped = running(HvacControlState.CORTE_INERCIA_TERMICA, HvacMode.APAGADO, Duration.ofMinutes(2));
        HvacResolution resolution = chain.proceed(request(true, false, false, stopped,
                predicted(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, 20), ANDEAN, room(20, 18, 50)));
        assertEquals(HvacControlState.CORTE_INERCIA_TERMICA, resolution.state());
        assertEquals("CompressorProtectionRule", resolution.decidedBy());
    }

    @Test
    @DisplayName("En el primer frame de un salón no hay ciclo previo que proteger")
    void firstDecisionIsNotHeld() {
        HvacSetting fresh = running(HvacControlState.APAGADO, HvacMode.APAGADO, Duration.ZERO);
        HvacResolution resolution = chain.proceed(new HvacRequest(true, false, false, fresh,
                predicted(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, 20), ANDEAN, room(20, 17, 50), true, NOW));
        assertEquals(HvacControlState.CONFORT_ACTIVO, resolution.state());
    }

    @Test
    @DisplayName("La familia andina decide el modo: calefacción a 20 °C")
    void familyChoosesTheMode() {
        HvacResolution resolution = chain.proceed(request(true, false, false, idleForLong(),
                predicted(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, 20), ANDEAN, room(20, 17, 50)));
        assertEquals(HvacMode.CALEFACCION, resolution.mode());
        assertEquals(20.0, resolution.setpointC());
        assertEquals("FamilyStrategyRule", resolution.decidedBy());
    }

    @Test
    @DisplayName("La familia tropical deshumidifica un salón húmedo aunque la temperatura esté bien")
    void tropicalFamilyDehumidifies() {
        HvacResolution resolution = chain.proceed(request(true, false, false, idleForLong(),
                predicted(HvacControlState.CORTE_INERCIA_TERMICA, HvacMode.APAGADO, 25), TROPICAL, room(30, 25, 82)));
        assertEquals(HvacControlState.CONFORT_ACTIVO, resolution.state());
        assertEquals(HvacMode.DESHUMIDIFICACION, resolution.mode());
    }

    @Test
    @DisplayName("Sin reglas especiales, decide el control predictivo")
    void predictiveControlIsTheEndOfTheChain() {
        HvacResolution resolution = chain.proceed(request(true, false, false, idleForLong(),
                predicted(HvacControlState.PRE_CALENTAMIENTO_IA, HvacMode.CALEFACCION, 19.5), ANDEAN, room(0, 18, 50)));
        assertEquals(HvacControlState.PRE_CALENTAMIENTO_IA, resolution.state());
        assertEquals("PredictiveControlRule", resolution.decidedBy());
    }

    @Test
    @DisplayName("Una cadena sin regla terminal falla explícitamente")
    void chainWithoutTerminalRuleFails() {
        HvacSettingChain broken = new HvacSettingChain(List.of(new MaintenanceRule()));
        assertThrows(IllegalStateException.class, () -> broken.proceed(request(true, false, false, idleForLong(),
                predicted(HvacControlState.APAGADO, HvacMode.APAGADO, 21), ANDEAN, room(0, 20, 50))));
    }
}
