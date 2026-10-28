package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.escenario.EscenarioOperativo;
import edu.campusnexo.domain.escenario.ReconocedorDeEscenarios;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TwinWatchdogServiceTest {
    private final TwinTestFixture fixture = new TwinTestFixture();
    private final InMemoryAlertRepository alertRows = new InMemoryAlertRepository();
    private final TwinWatchdogService watchdog = new TwinWatchdogService(fixture.clock, fixture.twins,
            fixture.reevaluation, new AlertRecorder(alertRows), Duration.ofSeconds(30), Duration.ofMinutes(15));

    private void frame() {
        fixture.pipeline.process(new TelemetrySnapshot("LAB-IA-102", fixture.clock.instant(), true, List.of(), 600, 21.0,
                14.0, 55.0, 300.0, 0, 1.0), "HTTPS · prueba");
    }

    @Test
    @DisplayName("Una cámara que deja de enviar se marca caída, se reconoce FALLO_CAMARA y queda la alerta")
    void silentCameraIsDetected() {
        frame();
        fixture.clock.advance(Duration.ofMinutes(3));

        assertEquals(1, watchdog.vigilar());

        SpaceTwinState twin = fixture.twins.findTwin("LAB-IA-102").orElseThrow();
        assertFalse(twin.cameraOnline());
        assertTrue(ReconocedorDeEscenarios.estandar().reconocer(twin, fixture.clock.instant())
                .contains(EscenarioOperativo.FALLO_CAMARA));
        assertEquals(AlertType.FALLO_CAMARA, alertRows.rows().getFirst().type());
    }

    @Test
    @DisplayName("Los salones que reportan o que nunca tuvieron dispositivos no se tocan")
    void leavesReportingAndSilentRoomsAlone() {
        frame();
        fixture.clock.advance(Duration.ofSeconds(10));

        assertEquals(0, watchdog.vigilar(), "reportó hace 10 s");
        assertTrue(alertRows.rows().isEmpty());
    }

    @Test
    @DisplayName("Un salón callado sin cámara caída se reevalúa por el tiempo, sin alerta")
    void quietRoomIsReevaluatedWithoutAlert() {
        frame();
        fixture.clock.advance(Duration.ofSeconds(45));

        assertEquals(1, watchdog.vigilar());
        assertTrue(fixture.twins.findTwin("LAB-IA-102").orElseThrow().cameraOnline());
        assertTrue(alertRows.rows().isEmpty());
    }
}
