package edu.campusnexo.infrastructure.adapter.out.history;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import edu.campusnexo.application.port.out.TwinHistoryPort.OccupancySample;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.Presence;
import edu.campusnexo.domain.twin.Sensing;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** Against a real MongoDB in a container: the history is plain documents the AI evaluation reads back. */
class MongoTwinHistoryAdapterTest {
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:8")
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1));
    private static MongoClient client;

    @BeforeAll
    static void start() {
        MONGO.start();
        client = MongoClients.create("mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017));
    }

    @AfterAll
    static void stop() {
        client.close();
        MONGO.stop();
    }

    @Test
    void storesEveryChangeAndReadsTheRoomBackInOrder() {
        MongoTwinHistoryAdapter history = new MongoTwinHistoryAdapter(client.getDatabase("historial_" + UUID.randomUUID()));
        history.createIndexes();
        SpaceTwinState empty = SpaceTwinState.initial(TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30),
                TestData.NOW);
        SpaceTwinState inClass = empty.toBuilder()
                .occupancy(new OccupancyBelief(25, 1.0, TestData.NOW.plusSeconds(60)))
                .presence(new Presence(1, 24))
                .sensing(new Sensing(true, TestData.NOW.plusSeconds(60), null, true))
                .updatedAt(TestData.NOW.plusSeconds(60))
                .build();

        history.publishTwinUpdate(inClass);
        history.publishTwinUpdate(empty);
        history.publishTrace(new ArchitectureTrace(UUID.randomUUID(), "camara", "301-A", TestData.NOW, List.of()));

        List<OccupancySample> samples = history.occupancy("301-A", TestData.NOW, TestData.NOW.plus(Duration.ofHours(1)));
        assertEquals(2, samples.size());
        assertEquals(TestData.NOW, samples.get(0).at());
        OccupancySample clase = samples.get(1);
        assertEquals(25, clase.people());
        assertEquals(1, clase.teachers());
        assertEquals(24, clase.students());
        assertTrue(clase.emulated());
        assertEquals(List.of("CLASE_EN_CURSO"), clase.escenarios());
        assertTrue(history.occupancy("303-A", TestData.NOW, TestData.NOW.plus(Duration.ofHours(1))).isEmpty());
    }

    @Test
    void aDownDatabaseNeverBreaksThePipeline() {
        try (MongoClient unreachable = MongoClients.create("mongodb://localhost:1/?serverSelectionTimeoutMS=200")) {
            MongoTwinHistoryAdapter history = new MongoTwinHistoryAdapter(unreachable.getDatabase("x"));
            assertDoesNotThrow(() -> history.publishTwinUpdate(SpaceTwinState.initial(
                    TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30), TestData.NOW)));
        }
    }
}
