package edu.campusnexo.application.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.ZoneDeskState;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationServiceTest {
    private final TwinTestFixture fixture = new TwinTestFixture();

    private SpaceTwinState run(SimulationScenario scenario) {
        SimulationResult result = fixture.simulation.run(scenario);
        assertNotNull(result.trace());
        fixture.clock.advance(Duration.ofSeconds(30));
        return result.spaces().get(0).state();
    }

    @Test
    @DisplayName("Clase en curso: ocupación óptima y corte por inercia térmica")
    void classInProgress() {
        SpaceTwinState state = run(SimulationScenario.CLASE_EN_CURSO);
        assertEquals(SpaceStatus.OCUPADO_OPTIMO, state.status());
        assertEquals(HvacControlState.CORTE_INERCIA_TERMICA, state.hvacState());
    }

    @Test
    @DisplayName("CO2 alto y fallo de cámara en la misma aula")
    void co2AndCameraFailure() {
        assertEquals(HvacControlState.VENTILACION_CO2_ACTIVA, run(SimulationScenario.CO2_ALTO).hvacState());
        SpaceTwinState offline = run(SimulationScenario.FALLO_CAMARA);
        assertEquals(false, offline.cameraOnline());
        assertEquals(2, offline.lastKalmanStep().measurements().size());
    }

    @Test
    @DisplayName("No-show, pre-enfriamiento y override en el aula tropical")
    void tropicalRoomScenarios() {
        SpaceTwinState noShow = run(SimulationScenario.NO_SHOW);
        assertEquals(SpaceStatus.LIBERADO_POR_NO_SHOW, noShow.status());
        assertEquals(HvacControlState.BLOQUEO_AHORRO_VACIO, noShow.hvacState());
        assertEquals(HvacControlState.PRE_ENFRIAMIENTO_IA, run(SimulationScenario.PRE_ENFRIAMIENTO).hvacState());
        assertEquals(HvacControlState.OVERRIDE_MANUAL_DOCENTE, run(SimulationScenario.OVERRIDE_DOCENTE).hvacState());
    }

    @Test
    @DisplayName("Sobrecupo y mantenimiento en el laboratorio")
    void labScenarios() {
        assertEquals(SpaceStatus.ALERTA_SOBRECUPO, run(SimulationScenario.SOBRECUPO).status());
        SpaceTwinState maintenance = run(SimulationScenario.MANTENIMIENTO);
        assertEquals(SpaceStatus.MANTENIMIENTO, maintenance.status());
        assertEquals(HvacControlState.APAGADO, maintenance.hvacState());
    }

    @Test
    @DisplayName("Subutilización y apertura como sala pop-up de parciales")
    void mildRoomScenarios() {
        assertEquals(SpaceStatus.ALERTA_SUBUTILIZADO, run(SimulationScenario.SUBUTILIZADO).status());
        assertEquals(SpaceStatus.HABILITADO_POPUP_PARCIALES, run(SimulationScenario.POPUP_PARCIALES).status());
    }

    @Test
    @DisplayName("Biblioteca: un puesto liberado por abandono, uno en alerta y tres verificados")
    void libraryHoarding() {
        SpaceTwinState state = run(SimulationScenario.ACAPARAMIENTO_BIBLIOTECA);
        Map<String, DeskObservation> desks = state.desks().stream()
                .collect(Collectors.toMap(DeskObservation::zoneId, Function.identity()));
        assertEquals(8, desks.size());
        assertEquals(ZoneDeskState.LIBERADO_POR_ABANDONO, desks.get("BIB2-M01").state());
        assertEquals(ZoneDeskState.ALERTA_ACAPARAMIENTO, desks.get("BIB2-M02").state());
        assertEquals(ZoneDeskState.OCUPADO_VERIFICADO, desks.get("BIB2-M03").state());
        assertEquals(ZoneDeskState.DISPONIBLE, desks.get("BIB2-M08").state());
        assertEquals(37, state.occupancy().people());
    }

    @Test
    @DisplayName("Reiniciar deja todos los espacios libres y borra las reservas de simulación")
    void reset() {
        run(SimulationScenario.NO_SHOW);
        run(SimulationScenario.MANTENIMIENTO);
        SimulationResult result = fixture.simulation.run(SimulationScenario.REINICIAR);
        assertEquals(5, result.spaces().size());
        assertTrue(result.spaces().stream().allMatch(view -> view.state().status() == SpaceStatus.LIBRE));
        assertTrue(result.spaces().stream().allMatch(view -> view.activeReservation() == null));
    }
}
