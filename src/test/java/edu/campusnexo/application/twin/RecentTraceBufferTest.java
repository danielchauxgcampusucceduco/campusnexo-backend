package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecentTraceBufferTest {
    @Test
    void retainsOnlyTheNewestTracesInReverseChronologicalInsertionOrder() {
        RecentTraceBuffer buffer = new RecentTraceBuffer(2);
        ArchitectureTrace first = trace("first");
        ArchitectureTrace second = trace("second");
        ArchitectureTrace third = trace("third");

        buffer.add(first);
        buffer.add(second);
        buffer.add(third);

        assertEquals(List.of(third, second), buffer.latest(10));
        assertEquals(List.of(third), buffer.latest(1));
    }

    @Test
    void requiresPositiveCapacityAndReturnsAnImmutableSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> new RecentTraceBuffer(0));
        RecentTraceBuffer buffer = new RecentTraceBuffer(1);
        buffer.add(trace("only"));

        List<ArchitectureTrace> snapshot = buffer.latest(1);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(trace("mutated")));
        assertTrue(buffer.latest(0).isEmpty());
    }

    private static ArchitectureTrace trace(String trigger) {
        return new ArchitectureTrace(UUID.randomUUID(), trigger, "A-301", Instant.EPOCH, List.of());
    }
}
