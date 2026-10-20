package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.ClimateProfile;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThermalInertiaMpcDomainServiceTest {
    private final ThermalInertiaMpcDomainService mpc = new ThermalInertiaMpcDomainService(ThermalParameters.defaults());
    private final ComfortBand andean = ComfortBand.forProfile(ClimateProfile.SEDE_ANDINA);
    private final ComfortBand tropical = ComfortBand.forProfile(ClimateProfile.SEDE_TROPICAL);

    @Test
    @DisplayName("Sin climatizar, la temperatura tiende exponencialmente a la exterior")
    void freeRunningConvergesToOutdoor() {
        List<Double> trajectory = mpc.freeRunning(30, 20, 0, 50, 600);
        assertEquals(30.0, trajectory.get(0));
        assertTrue(trajectory.get(trajectory.size() - 1) < 20.2);
        for (int i = 1; i < trajectory.size(); i++) {
            assertTrue(trajectory.get(i) <= trajectory.get(i - 1));
        }
    }

    @Test
    @DisplayName("Si la inercia térmica cubre el resto de la clase, se corta el compresor")
    void thermalInertiaCut() {
        ThermalDecision decision = mpc.decide(new ThermalContext(22.5, 14, 26, 48, 780, andean, 80, null));
        assertEquals(HvacControlState.CORTE_INERCIA_TERMICA, decision.state());
        assertTrue(decision.predictedMinC() >= andean.lowC());
    }

    @Test
    @DisplayName("Si la temperatura saldría de la banda antes de terminar, se mantiene el confort activo")
    void comfortWhenTrajectoryLeavesBand() {
        ThermalDecision decision = mpc.decide(new ThermalContext(19.5, 8, 5, 48, 600, andean, 120, null));
        assertEquals(HvacControlState.CONFORT_ACTIVO, decision.state());
    }

    @Test
    @DisplayName("Con CO2 alto y temperatura en banda, la ventilación tiene prioridad")
    void co2Ventilation() {
        assertEquals(HvacControlState.VENTILACION_CO2_ACTIVA,
                mpc.decide(new ThermalContext(22.8, 14, 30, 48, 1450, andean, 60, null)).state());
    }

    @Test
    @DisplayName("Aula tropical vacía y caliente con clase próxima: pre-enfriamiento")
    void preCooling() {
        ThermalDecision decision = mpc.decide(new ThermalContext(28.5, 32, 0, 72, 430, tropical, null, 20));
        assertEquals(HvacControlState.PRE_ENFRIAMIENTO_IA, decision.state());
        assertEquals(25.5, decision.setpointC());
    }

    @Test
    @DisplayName("Entre clases sin necesidad de pre-enfriar: setback ecológico")
    void ecoSetback() {
        assertEquals(HvacControlState.ECO_SETBACK_ENTRE_CLASES,
                mpc.decide(new ThermalContext(24.0, 25, 0, 72, 430, tropical, null, 40)).state());
    }

    @Test
    @DisplayName("Vacío y sin clases próximas: bloqueo por ahorro")
    void emptyBlocked() {
        assertEquals(HvacControlState.BLOQUEO_AHORRO_VACIO,
                mpc.decide(new ThermalContext(27, 31.8, 0, 72, 440, tropical, null, null)).state());
        assertEquals(HvacControlState.BLOQUEO_AHORRO_VACIO,
                mpc.decide(new ThermalContext(27, 31.8, 0, 72, 440, tropical, null, 180)).state());
    }
}
