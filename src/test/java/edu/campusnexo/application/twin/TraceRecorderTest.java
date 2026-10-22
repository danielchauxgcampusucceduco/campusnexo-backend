package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.trace.HexagonalLayer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceRecorderTest {
    @Test
    void recordsOrderedArchitectureHopsAndOutboundPortAdapterPairs() {
        TraceRecorder recorder = new TraceRecorder("REST", "A-301", Instant.EPOCH);
        recorder.step(HexagonalLayer.INBOUND_ADAPTER, "TwinController", "accepted")
                .step(HexagonalLayer.INPUT_PORT, "TwinQueryUseCase", "delegated")
                .outbound("SpaceRepositoryPort", new FakeSpaceRepository(), "loaded");

        var trace = recorder.finish();

        assertNotNull(trace.traceId());
        assertEquals("REST", trace.trigger());
        assertEquals("A-301", trace.spaceId());
        assertEquals(List.of(HexagonalLayer.INBOUND_ADAPTER, HexagonalLayer.INPUT_PORT,
                        HexagonalLayer.OUTPUT_PORT, HexagonalLayer.OUTBOUND_ADAPTER),
                trace.steps().stream().map(step -> step.layer()).toList());
        assertEquals("FakeSpaceRepository", trace.steps().get(3).component());
        assertTrue(trace.steps().stream().allMatch(step -> step.offsetMicros() >= 0));
    }

    @Test
    void completedTraceCannotBeMutatedByItsConsumer() {
        var trace = new TraceRecorder("REST", null, Instant.EPOCH)
                .step(HexagonalLayer.USE_CASE, "Query", "done")
                .finish();

        assertThrows(UnsupportedOperationException.class, () -> trace.steps().clear());
    }

    private static final class FakeSpaceRepository {
    }
}
