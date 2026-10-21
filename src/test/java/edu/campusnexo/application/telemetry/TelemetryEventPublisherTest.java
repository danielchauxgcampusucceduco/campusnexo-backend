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

    @Test
    @DisplayName("Una entrega que falla se reintenta; solo se reporta si falla en todos los intentos")
    void retriesBeforeReporting() {
        List<String> calls = new ArrayList<>();
        List<RuntimeException> reported = new ArrayList<>();
        int[] failuresLeft = {2};
        TelemetryObserver flaky = event -> {
            calls.add("flaky");
            if (failuresLeft[0]-- > 0) {
                throw new IllegalStateException("transitorio");
            }
        };
        TelemetryObserver broken = event -> {
            throw new IllegalStateException("roto");
        };
        TelemetryEventPublisher publisher = new TelemetryEventPublisher(List.of(flaky, broken),
                (observer, event, failure) -> reported.add(failure), Runnable::run, 3, java.time.Duration.ZERO);

        publisher.publish(new TelemetryRecorded(TestData.reading("AULA-301", TestData.NOW, 10, 600, 1),
                TestData.andeanClassroom()));

        assertEquals(3, calls.size());
        assertEquals(1, reported.size());
        assertEquals("roto", reported.get(0).getMessage());
    }

    @Test
    @DisplayName("Las entregas se despachan al ejecutor: la ingesta no espera a los observadores")
    void dispatchesOnExecutor() {
        List<Runnable> queued = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        TelemetryEventPublisher publisher = new TelemetryEventPublisher(List.of(event -> calls.add("observer")),
                (observer, event, failure) -> { }, queued::add, 1, java.time.Duration.ZERO);

        publisher.publish(new TelemetryRecorded(TestData.reading("AULA-301", TestData.NOW, 10, 600, 1),
                TestData.andeanClassroom()));
        assertEquals(0, calls.size());
        queued.forEach(Runnable::run);
        assertEquals(List.of("observer"), calls);
    }
}
