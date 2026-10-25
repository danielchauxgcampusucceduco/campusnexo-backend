package edu.campusnexo.application.port.out;

import java.time.Duration;

/**
 * Counters an operator needs to trust the twin: readings dropped and why, write conflicts between
 * instances, how often the AI service is skipped and how long a camera frame takes to reach the twin. Implemented with Micrometer and exposed in Prometheus
 * format; the application only states what happened.
 */
public interface OperationalMetrics {
    /** A reading was not applied: {@code reason} is {@code duplicada}, {@code fuera_de_orden} or {@code reemplazada}. */
    void readingDiscarded(String source, String reason);

    /** A twin write lost against another writer and was recomputed. */
    void twinWriteConflict();

    /** An occupancy estimate came from the local fallback instead of the AI service. */
    void aiFallback(String reason);

    /** The circuit breaker in front of the AI service changed state. */
    void aiCircuitState(String state);

    /** From the frame captured at the edge to the twin published (QA-2); edge and server clocks compared. */
    void frameLatency(Duration latency);

    static OperationalMetrics none() {
        return new OperationalMetrics() {
            @Override
            public void readingDiscarded(String source, String reason) {
            }

            @Override
            public void twinWriteConflict() {
            }

            @Override
            public void aiFallback(String reason) {
            }

            @Override
            public void aiCircuitState(String state) {
            }

            @Override
            public void frameLatency(Duration latency) {
            }
        };
    }
}
