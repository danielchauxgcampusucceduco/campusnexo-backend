package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.application.port.out.UnitOfWork;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmulatedDataServiceTest {
    @Test
    @DisplayName("Salir de la demo borra lo emulado y reinicia el gemelo; un salón con cámara real no se toca")
    void purgeErasesOnlyEmulatedRooms() {
        TwinTestFixture fixture = new TwinTestFixture();
        String emulatedRoom = fixture.catalog.listSpaces(null).get(0).spaceId();
        String realRoom = fixture.catalog.listSpaces(null).get(1).spaceId();
        fixture.pipeline.process(frame(emulatedRoom, fixture.clock.instant(), true), "demo");
        fixture.pipeline.process(frame(realRoom, fixture.clock.instant(), false), "camara");

        List<String> erased = new ArrayList<>();
        List<String> forgotten = new ArrayList<>();
        TwinHistoryPort history = new TwinHistoryPort() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<OccupancySample> occupancy(String spaceId, Instant from, Instant to) {
                return List.of();
            }

            @Override
            public void forgetEmulated(String spaceId) {
                forgotten.add(spaceId);
            }
        };
        EmulatedDataService service = new EmulatedDataService(fixture.twins, erased::add, history, fixture.controls,
                UnitOfWork.direct());

        assertEquals(List.of(emulatedRoom), service.purge());
        assertEquals(List.of(emulatedRoom), erased);
        assertEquals(List.of(emulatedRoom), forgotten);
        SpaceTwinState reset = fixture.twins.findTwin(emulatedRoom).orElseThrow();
        assertFalse(reset.sensing().emulated());
        assertFalse(reset.sensing().reporting());
        assertEquals(0, reset.occupancy().people());
        assertTrue(fixture.twins.findTwin(realRoom).orElseThrow().sensing().reporting());
        assertEquals(List.of(), service.purge());
    }

    private static TelemetrySnapshot frame(String spaceId, Instant at, boolean emulated) {
        return new TelemetrySnapshot(spaceId, at, true, List.of(), 600, 21, null, 50, 400, 3, 0.2, true, 0, emulated);
    }
}
