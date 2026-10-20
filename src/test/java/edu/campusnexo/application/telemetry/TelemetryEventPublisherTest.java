package edu.campusnexo.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import edu.campusnexo.support.TestData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryEventPublisherTest {

    @Test
    @DisplayName("Un observador que falla se reporta y no impide notificar a los demás")
    void failingObserverDoesNotStopOthers() {
        List<String> calls = new ArrayList<>();
        List<RuntimeException> reported = new ArrayList<>();
        TelemetryObserver failing = event -> {
            calls.add("failing");
            throw new IllegalStateException("boom");
        };
        TelemetryObserver healthy = event -> calls.add("healthy");
        TelemetryEventPublisher publisher = new TelemetryEventPublisher(List.of(failing, healthy),
                (observer, event, failure) -> reported.add(failure));

        publisher.publish(new TelemetryRecorded(TestData.reading("AULA-301", TestData.NOW, 10, 600, 1),
                TestData.andeanClassroom()));

        assertEquals(List.of("failing", "healthy"), calls);
        assertEquals(1, reported.size());
        assertEquals("boom", reported.get(0).getMessage());
    }
}
