package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.port.out.TwinChangeNotifierPort;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.support.RecordingOutboundAdapters;
import edu.campusnexo.support.TestData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TwinPublisherTest {
    @Test
    @DisplayName("Si un seguidor del gemelo falla (el historial), los demás (el tablero) igual reciben el cambio")
    void aFailingFollowerDoesNotStopTheOthers() {
        TwinChangeNotifierPort broken = new TwinChangeNotifierPort() {
            @Override
            public void publishTwinUpdate(SpaceTwinState state) {
                throw new IllegalStateException("MongoDB no responde");
            }

            @Override
            public void publishTrace(ArchitectureTrace trace) {
                throw new IllegalStateException("MongoDB no responde");
            }
        };
        RecordingOutboundAdapters.Notifier dashboard = new RecordingOutboundAdapters.Notifier();
        TwinPublisher publisher = new TwinPublisher(new InMemorySpaceRepositoryPort(),
                new RecordingOutboundAdapters.Actuator(), new RecordingOutboundAdapters.EquipmentActuator(),
                List.of(broken, dashboard), new RecentTraceBuffer(10));
        SpaceTwinState previous = SpaceTwinState.initial(TestData.room("303-A", SpaceType.AULA, 36, 6, 6, 1), TestData.NOW);

        publisher.publish(new TwinOutcome(previous, previous, null,
                new TraceRecorder("prueba", "303-A", TestData.NOW)));

        assertEquals(1, dashboard.updates.size());
        assertEquals(1, dashboard.traces.size());
        assertTrue(dashboard.traces.getFirst().steps().stream().anyMatch(step -> step.detail().startsWith("Omitido")));
    }
}
