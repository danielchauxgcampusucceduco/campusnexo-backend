package edu.campusnexo.application.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.Requester;
import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.reservation.ReservationOrigin;
import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.ZoneDeskState;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.TestData;
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
        SimulationResult result = fixture.simulation.run(scenario, "REST · prueba");
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
        assertEquals(15, seated(crowded));
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
        assertEquals("423-A", command.spaceId());
        assertTrue(command.lightsOff());
        assertFalse(command.suspendDevices());
        assertFalse(state.equipment().lightsOn());
        assertEquals(RoomCondition.LIBRE, state.condition());
    }

    @Test
    @DisplayName("Sala de cómputo vacía con 24 equipos encendidos: se suspenden con las luces")
    void computersLeftOn() {
        SpaceTwinState state = run(SimulationScenario.EQUIPOS_ENCENDIDOS);
        EquipmentCommand command = fixture.equipment.commands.get(0);
        assertTrue(command.lightsOff());
        assertTrue(command.suspendDevices());
        assertEquals(0, state.equipment().devicesOn());
        assertEquals(24, state.equipment().devicesTotal());
    }

    @Test
    @DisplayName("Biblioteca: una mesa liberada por abandono, una en alerta y tres verificadas")
    void libraryHoarding() {
        SpaceTwinState state = run(SimulationScenario.ACAPARAMIENTO_BIBLIOTECA);
        Map<String, DeskObservation> desks = state.desks().stream()
                .collect(Collectors.toMap(DeskObservation::zoneId, Function.identity()));
        assertEquals(8, desks.size());
        assertEquals(ZoneDeskState.LIBERADO_POR_ABANDONO, desks.get("BIB2B-M01").state());
        assertEquals(ZoneDeskState.ALERTA_ACAPARAMIENTO, desks.get("BIB2B-M02").state());
        assertEquals(ZoneDeskState.OCUPADO_VERIFICADO, desks.get("BIB2B-M03").state());
        assertEquals(ZoneDeskState.DISPONIBLE, desks.get("BIB2B-M08").state());
        assertEquals(33, state.occupancy().people());
    }

    @Test
    @DisplayName("Reiniciar deja todos los salones libres y borra las reservas de simulación")
    void reset() {
        run(SimulationScenario.NO_SHOW);
        run(SimulationScenario.MANTENIMIENTO);
        SimulationResult result = fixture.simulation.run(SimulationScenario.REINICIAR, "REST · prueba");
        assertEquals(TestData.scenarioRooms().size(), result.spaces().size());
        assertTrue(result.spaces().stream().allMatch(view -> view.space().simulationAllowed()));
        assertTrue(result.spaces().stream().allMatch(view -> view.state().status() == SpaceStatus.LIBRE));
        assertTrue(result.spaces().stream().allMatch(view -> view.activeReservation() == null));
    }

    @Test
    @DisplayName("Un salón que no es de la demostración no se toca: el escenario se rechaza sin cambiar nada")
    void refusesRoomsOutsideTheDemonstration() {
        SimulationService restricted = new SimulationService(fixture.clock, fixture.pipeline, fixture.environment,
                fixture.controls, fixture.reservations, new SpaceCatalogService(new InMemorySpaceCatalogRepository(
                        TestData.room("421-A", SpaceType.AULA, 30, 5, 6, 1))), fixture.queries);

        ConflictException refused = assertThrows(ConflictException.class,
                () -> restricted.run(SimulationScenario.CLASE_EN_CURSO, "REST · prueba"));

        assertEquals("simulation_space_not_allowed", refused.code());
        assertTrue(fixture.twins.findAllTwins().isEmpty());
        assertTrue(fixture.reservationRows.findOverlapping("421-A", TestData.NOW.minusSeconds(86_400),
                TestData.NOW.plusSeconds(86_400)).isEmpty());
    }

    @Test
    @DisplayName("Reiniciar no toca los salones reales ni las reservas de las personas, aunque su texto empiece por SIM-")
    void resetLeavesRealRoomsAndPeoplesBookingsAlone() {
        fixture.controls.setMaintenance("AULA-301", true);
        Reservation booking = fixture.reservations.create(new CreateReservationCommand("421-A",
                TestData.NOW.plus(Duration.ofHours(3)), TestData.NOW.plus(Duration.ofHours(4)), 20, "SIM-PARCIAL",
                ReservationOrigin.USUARIO, Requester.user("docente")));

        fixture.simulation.run(SimulationScenario.REINICIAR, "REST · prueba");

        assertTrue(fixture.queries.space("AULA-301").state().maintenance(), "AULA-301 no es de la demostración");
        assertEquals(booking, fixture.reservations.get(booking.reservationId()));
    }

    @Test
    @DisplayName("Si un salón de la demo tiene una reserva real en el horario, el escenario no se ejecuta")
    void realBookingBlocksTheScenario() {
        fixture.reservations.create(new CreateReservationCommand("421-A", TestData.NOW.minus(Duration.ofMinutes(5)),
                TestData.NOW.plus(Duration.ofMinutes(50)), 20, "IS-701", ReservationOrigin.USUARIO,
                Requester.user("docente")));

        ConflictException blocked = assertThrows(ConflictException.class,
                () -> fixture.simulation.run(SimulationScenario.CLASE_EN_CURSO, "REST · prueba"));

        assertEquals("simulation_blocked_by_reservation", blocked.code());
        assertTrue(fixture.twins.findAllTwins().isEmpty(), "nada se escribió antes de rechazar");
    }

    @Test
    @DisplayName("Los escenarios pasan por los puertos de entrada: la traza nombra el adaptador que los lanzó")
    void scenariosGoThroughTheInputPorts() {
        SimulationResult result = fixture.simulation.run(SimulationScenario.CO2_ALTO, "REST · SimulationController");
        assertEquals("REST · SimulationController (lectura Arduino)", result.trace().trigger());
        assertEquals("422-A", result.trace().spaceId());
    }
}
