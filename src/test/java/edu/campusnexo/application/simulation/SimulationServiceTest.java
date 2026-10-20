package edu.campusnexo.application.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.RoomCondition;
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

    private static long seated(SpaceTwinState state) {
        return state.desks().stream().filter(d -> d.state() == ZoneDeskState.OCUPADO_VERIFICADO).count();
    }

    @Test
    @DisplayName("Clase en curso: 26 pupitres ocupados, luces encendidas y corte por inercia térmica")
    void classInProgress() {
        SpaceTwinState state = run(SimulationScenario.CLASE_EN_CURSO);
        assertEquals(SpaceStatus.OCUPADO_OPTIMO, state.status());
        assertEquals(HvacControlState.CORTE_INERCIA_TERMICA, state.hvacState());
        assertEquals(RoomCondition.OCUPADO, state.condition());
        assertTrue(state.equipment().lightsOn());
        assertEquals(30, state.desks().size());
        assertEquals(26, seated(state));
    }

    @Test
    @DisplayName("CO2 alto y fallo de cámara")
    void co2AndCameraFailure() {
        assertEquals(HvacControlState.VENTILACION_CO2_ACTIVA, run(SimulationScenario.CO2_ALTO).hvacState());
        SpaceTwinState offline = run(SimulationScenario.FALLO_CAMARA);
        assertFalse(offline.cameraOnline());
        assertEquals(2, offline.lastKalmanStep().measurements().size());
    }

    @Test
    @DisplayName("No-show libera el salón; el docente toma el control del clima")
    void reservationsAndOverride() {
        SpaceTwinState noShow = run(SimulationScenario.NO_SHOW);
        assertEquals(SpaceStatus.LIBERADO_POR_NO_SHOW, noShow.status());
        assertEquals(HvacControlState.BLOQUEO_AHORRO_VACIO, noShow.hvacState());
        assertEquals(HvacControlState.OVERRIDE_MANUAL_DOCENTE, run(SimulationScenario.OVERRIDE_DOCENTE).hvacState());
    }

    @Test
    @DisplayName("Sobrecupo y mantenimiento en los laboratorios del bloque B")
    void labScenarios() {
        SpaceTwinState crowded = run(SimulationScenario.SOBRECUPO);
        assertEquals(SpaceStatus.ALERTA_SOBRECUPO, crowded.status());
        assertEquals(RoomCondition.SOBRECUPO, crowded.condition());
        assertEquals(24, seated(crowded));
        SpaceTwinState maintenance = run(SimulationScenario.MANTENIMIENTO);
        assertEquals(SpaceStatus.MANTENIMIENTO, maintenance.status());
        assertEquals(RoomCondition.MANTENIMIENTO, maintenance.condition());
        assertEquals(HvacControlState.APAGADO, maintenance.hvacState());
    }

    @Test
    @DisplayName("Subutilización y apertura como sala de estudio para parciales")
    void underuseAndPopUp() {
        assertEquals(SpaceStatus.ALERTA_SUBUTILIZADO, run(SimulationScenario.SUBUTILIZADO).status());
        assertEquals(SpaceStatus.HABILITADO_POPUP_PARCIALES, run(SimulationScenario.POPUP_PARCIALES).status());
    }

    @Test
    @DisplayName("Luces encendidas en un salón vacío: el Arduino recibe la orden de apagarlas")
    void lightsLeftOn() {
        SpaceTwinState state = run(SimulationScenario.LUCES_ENCENDIDAS);
        assertEquals(1, fixture.equipment.commands.size());
        EquipmentCommand command = fixture.equipment.commands.get(0);
        assertEquals("204-A", command.spaceId());
        assertTrue(command.lightsOff());
        assertFalse(command.suspendDevices());
        assertFalse(state.equipment().lightsOn());
        assertEquals(RoomCondition.LIBRE, state.condition());
    }

    @Test
    @DisplayName("Sala de cómputo vacía con 30 equipos encendidos: se suspenden con las luces")
    void computersLeftOn() {
        SpaceTwinState state = run(SimulationScenario.EQUIPOS_ENCENDIDOS);
        EquipmentCommand command = fixture.equipment.commands.get(0);
        assertTrue(command.lightsOff());
        assertTrue(command.suspendDevices());
        assertEquals(0, state.equipment().devicesOn());
        assertEquals(30, state.equipment().devicesTotal());
    }

    @Test
    @DisplayName("Biblioteca: una mesa liberada por abandono, una en alerta y tres verificadas")
    void libraryHoarding() {
        SpaceTwinState state = run(SimulationScenario.ACAPARAMIENTO_BIBLIOTECA);
        Map<String, DeskObservation> desks = state.desks().stream()
                .collect(Collectors.toMap(DeskObservation::zoneId, Function.identity()));
        assertEquals(8, desks.size());
        assertEquals(ZoneDeskState.LIBERADO_POR_ABANDONO, desks.get("201B-M01").state());
        assertEquals(ZoneDeskState.ALERTA_ACAPARAMIENTO, desks.get("201B-M02").state());
        assertEquals(ZoneDeskState.OCUPADO_VERIFICADO, desks.get("201B-M03").state());
        assertEquals(ZoneDeskState.DISPONIBLE, desks.get("201B-M08").state());
        assertEquals(33, state.occupancy().people());
    }

    @Test
    @DisplayName("Reiniciar deja todos los salones libres y borra las reservas de simulación")
    void reset() {
        run(SimulationScenario.NO_SHOW);
        run(SimulationScenario.MANTENIMIENTO);
        SimulationResult result = fixture.simulation.run(SimulationScenario.REINICIAR);
        assertEquals(16, result.spaces().size());
        assertTrue(result.spaces().stream().allMatch(view -> view.state().status() == SpaceStatus.LIBRE));
        assertTrue(result.spaces().stream().allMatch(view -> view.activeReservation() == null));
    }
}
