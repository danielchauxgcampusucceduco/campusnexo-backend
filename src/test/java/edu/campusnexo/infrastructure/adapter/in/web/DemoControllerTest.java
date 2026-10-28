package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.SalaVista;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoControllerTest {
    @Test
    @DisplayName("Los frames de la demo entran al gemelo como sensor emulado durante todo el día simulado")
    void framesFeedTheTwin() {
        TwinTestFixture fixture = new TwinTestFixture();
        Duration day = Duration.ofMinutes(20);
        DemoSimulation simulation = new DemoSimulation(DemoSimulation.demoRooms(fixture.catalog.listSpaces(null), 8), day);
        assertTrue(!simulation.salones().isEmpty());
        for (int step = 0; step < 40; step++) {
            for (SalaVista sala : simulation.snapshot(fixture.clock.instant()).salones()) {
                TelemetrySnapshot frame = DemoController.frame(sala, fixture.clock.instant());
                assertTrue(frame.emulated());
                assertEquals(sala.ahora().personas(), frame.detections().size());
                SpaceTwinState state = fixture.pipeline.process(frame, DemoController.NAME);
                assertEquals(sala.spaceId(), state.spaceId());
            }
            fixture.clock.advance(day.dividedBy(40));
        }
    }
}
