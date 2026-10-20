package edu.campusnexo.domain.climate.policy;

import static edu.campusnexo.support.TestData.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThermalPolicyTest {
    private final ThermalPolicy heatingFirst = new HeatingFirstThermalPolicy(20.0, 25.0, 1.0);
    private final ThermalPolicy tropical = new CoolingWithDehumidificationPolicy(25.5, 70, 0.5);
    private final ThermalPolicy comfortBand = new ComfortBandThermalPolicy(20.0, 25.0, 2.0);

    @Test
    @DisplayName("Andina: un aula fría y ocupada pide calefacción")
    void heatingFirstHeatsColdOccupiedRoom() {
        HvacDecision decision = heatingFirst.recommend(context(20, 16.5, 12, 60, 300, 700));
        assertEquals(HvacMode.CALEFACCION, decision.mode());
        assertEquals(20.0, decision.setpointC());
    }

    @Test
    @DisplayName("Andina: dentro de la banda no se climatiza")
    void heatingFirstStaysOffInsideBand() {
        assertEquals(HvacMode.APAGADO, heatingFirst.recommend(context(20, 22, 14, 60, 300, 700)).mode());
    }

    @Test
    @DisplayName("Sin ocupación ninguna política climatiza")
    void emptyRoomsAreNeverConditioned() {
        assertEquals(HvacMode.APAGADO, heatingFirst.recommend(context(0, 10, 5, 60, 300, 450)).mode());
        assertEquals(HvacMode.APAGADO, tropical.recommend(context(0, 32, 33, 85, 300, 450)).mode());
        assertEquals(HvacMode.APAGADO, comfortBand.recommend(context(0, 28, 30, 60, 300, 450)).mode());
    }

    @Test
    @DisplayName("Tropical: calor con ocupación pide enfriamiento con setpoint decimal")
    void tropicalCools() {
        HvacDecision decision = tropical.recommend(context(30, 28, 32, 65, 300, 800));
        assertEquals(HvacMode.ENFRIAMIENTO, decision.mode());
        assertEquals(25.5, decision.setpointC());
    }

    @Test
    @DisplayName("Tropical: temperatura correcta pero humedad alta pide deshumidificar")
    void tropicalDehumidifies() {
        assertEquals(HvacMode.DESHUMIDIFICACION, tropical.recommend(context(30, 25, 30, 82, 300, 800)).mode());
    }

    @Test
    @DisplayName("Templada: si afuera está más fresco se prefiere enfriamiento libre")
    void comfortBandPrefersFreeCooling() {
        HvacDecision decision = comfortBand.recommend(context(25, 27, 21, 60, 300, 800));
        assertEquals(HvacMode.APAGADO, decision.mode());
        assertTrue(decision.explanation().contains("enfriamiento libre"));
    }

    @Test
    @DisplayName("Templada: si afuera también hace calor se enfría")
    void comfortBandCoolsWhenOutsideIsHot() {
        assertEquals(HvacMode.ENFRIAMIENTO, comfortBand.recommend(context(25, 27, 26.5, 60, 300, 800)).mode());
    }
}
