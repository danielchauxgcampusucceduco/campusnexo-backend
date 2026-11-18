package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoControllerTest {
    @Test
    @DisplayName("Los frames de la demo entran al gemelo como sensor emulado durante todo el día simulado")
    void framesFeedTheTwin() {
        TwinTestFixture fixture = new TwinTestFixture();
        Duration day = Duration.ofMinutes(20);
        // The timetable rooms onto the rooms of the twin fixture, one fixture room per timetable room in turn.
        List<String> rooms = fixture.catalog.listSpaces(null).stream()
                .filter(sp -> sp.type() == SpaceType.AULA || sp.type() == SpaceType.SALA_COMPUTO).map(CampusSpace::spaceId).toList();
        List<SesionDeClase> real = CsvHorarioAcademicoAdapter.cargar();
        List<String> salones = real.stream().map(SesionDeClase::salon).distinct().toList();
        List<SesionDeClase> horario = real.stream()
                .map(x -> new SesionDeClase(x.grupo(), x.estudiantes(), x.curso(), x.dia(), x.inicio(), x.fin(),
                        rooms.get(salones.indexOf(x.salon()) % rooms.size()), x.asignado()))
                .toList();
        DemoSimulation simulation = new DemoSimulation(DemoSimulation.demoRooms(fixture.catalog.listSpaces(null), horario),
                horario, day);
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
            fixture.clock.advance(day.dividedBy(40));
        }
    }
}
