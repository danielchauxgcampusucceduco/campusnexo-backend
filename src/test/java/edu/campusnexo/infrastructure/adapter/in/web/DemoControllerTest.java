package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.port.in.DisponibilidadUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.horario.SesionDeClase;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.SalaVista;
import edu.campusnexo.infrastructure.adapter.out.horario.CsvHorarioAcademicoAdapter;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoControllerTest {
    private static final Duration DAY = Duration.ofMinutes(20);

    /** The real timetable moved onto the rooms of the twin fixture, one fixture room per timetable room in turn. */
    private static List<SesionDeClase> horario(TwinTestFixture fixture) {
        List<String> rooms = fixture.catalog.listSpaces(null).stream()
                .filter(sp -> sp.type() == SpaceType.AULA || sp.type() == SpaceType.SALA_COMPUTO).map(CampusSpace::spaceId).toList();
        List<SesionDeClase> real = CsvHorarioAcademicoAdapter.cargar();
        List<String> salones = real.stream().map(SesionDeClase::salon).distinct().toList();
        return real.stream()
                .map(x -> new SesionDeClase(x.grupo(), x.estudiantes(), x.curso(), x.dia(), x.inicio(), x.fin(),
                        rooms.get(salones.indexOf(x.salon()) % rooms.size()), x.asignado()))
                .toList();
    }

    @Test
    @DisplayName("Los frames de la demo entran al gemelo como sensor emulado durante todo el día simulado")
    void framesFeedTheTwin() {
        TwinTestFixture fixture = new TwinTestFixture();
        List<SesionDeClase> horario = horario(fixture);
        DemoSimulation simulation = new DemoSimulation(DemoSimulation.demoRooms(fixture.catalog.listSpaces(null), horario),
                horario, DAY);
        assertTrue(!simulation.salones().isEmpty());
        for (int step = 0; step < 40; step++) {
            for (SalaVista sala : simulation.snapshot(fixture.clock.instant()).salones()) {
                TelemetrySnapshot frame = DemoController.frame(sala, fixture.clock.instant());
                assertTrue(frame.emulated());
                assertEquals(sala.ahora().camaraEnLinea(), frame.cameraOnline());
                assertEquals(sala.ahora().camaraEnLinea() ? sala.ahora().personas() : 0, frame.detections().size());
                SpaceTwinState state = fixture.pipeline.process(frame, DemoController.NAME);
                assertEquals(sala.spaceId(), state.spaceId());
            }
            fixture.clock.advance(DAY.dividedBy(40));
        }
    }

    @Test
    @DisplayName("Nada se emula hasta iniciar la demo a mano; salir o reiniciar el servidor borra lo emulado")
    void startsOnlyByHand() {
        TwinTestFixture fixture = new TwinTestFixture();
        List<SesionDeClase> horario = horario(fixture);
        List<String> purges = new ArrayList<>();
        DisponibilidadUseCase disponibilidad = new DisponibilidadUseCase() {
            public List<SesionDeClase> horario() {
                return horario;
            }

            public DisponibilidadDelCampus disponibilidad(LocalDate fecha) {
                throw new UnsupportedOperationException();
            }

            public List<String> vigilarHorario() {
                return List.of();
            }
        };
        DemoController demo = new DemoController(fixture.catalog, fixture.pipeline, fixture.queries,
                () -> {
                    purges.add("purga");
                    return List.of();
                }, disponibilidad, fixture.clock, DAY);

        demo.eraseLeftovers();
        assertEquals(1, purges.size(), "al arrancar se borra lo emulado que haya quedado");

        // Looking at the screen does not start it.
        assertFalse(demo.snapshot().activa());
        demo.feedTwin();
        assertTrue(fixture.telemetryRows.rows().isEmpty());

        assertTrue(demo.start().activa());
        demo.feedTwin();
        int fed = fixture.telemetryRows.rows().size();
        assertTrue(fed > 0, "iniciada, alimenta el gemelo");

        demo.leave();
        assertEquals(2, purges.size());
        assertFalse(demo.snapshot().activa());
        fixture.clock.advance(Duration.ofMinutes(1));
        demo.feedTwin();
        assertEquals(fed, fixture.telemetryRows.rows().size(), "después de salir no emula nada");
    }
}
