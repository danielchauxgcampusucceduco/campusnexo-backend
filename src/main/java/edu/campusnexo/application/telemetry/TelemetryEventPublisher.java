package edu.campusnexo.application.telemetry;

import edu.campusnexo.application.port.out.ObserverFailureReporter;
import edu.campusnexo.application.port.out.TelemetryObserver;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Observer subject: notifies independent observers after a reading is stored.
 *
 * <p>Each observer is delivered on the given {@link Executor}, so a slow observer does not add latency
 * to the ingestion request. A failing delivery is retried up to {@code maxAttempts} times with a short
 * linear backoff (observers must therefore be idempotent, as the alert observers are thanks to their
 * cooldown); if it still fails it is reported, and neither the other observers nor the stored reading are
 * affected. Delivery is at-most-{@code maxAttempts} in memory: a restart loses pending notifications,
 * which is acceptable for alerts derived again from the next reading. A durable outbox would be the next
 * step if an observer ever had to be exactly-once.
 */
public class TelemetryEventPublisher {
    private final List<TelemetryObserver> observers;
    private final ObserverFailureReporter failureReporter;
    private final Executor executor;
    private final int maxAttempts;
    private final Duration backoff;

    /** Synchronous, single attempt: for tests and tools that need the reactions before continuing. */
    public TelemetryEventPublisher(List<TelemetryObserver> observers, ObserverFailureReporter failureReporter) {
        this(observers, failureReporter, Runnable::run, 1, Duration.ZERO);
    }

    public TelemetryEventPublisher(List<TelemetryObserver> observers, ObserverFailureReporter failureReporter,
                                   Executor executor, int maxAttempts, Duration backoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
        this.observers = List.copyOf(observers);
        this.failureReporter = failureReporter;
        this.executor = executor;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    /**
     * Hands every observer the event without waiting for any of them. When the executor is saturated the
     * delivery is reported instead of blocking the caller: the alert will be derived again from the next
     * reading of the room, while a blocked camera pipeline would lose frames.
     */
    public void publish(TelemetryRecorded event) {
        for (TelemetryObserver observer : observers) {
            try {
                executor.execute(() -> deliver(observer, event));
            } catch (RejectedExecutionException saturated) {
                failureReporter.report(observer, event, saturated);
            }
        }
    }

    private void deliver(TelemetryObserver observer, TelemetryRecorded event) {
        for (int attempt = 1; ; attempt++) {
            try {
                observer.onTelemetryRecorded(event);
                return;
            } catch (RuntimeException failure) {
                if (attempt >= maxAttempts) {
                    failureReporter.report(observer, event, failure);
                    return;
                }
                pause(backoff.multipliedBy(attempt));
            }
        }
    }

    private static void pause(Duration wait) {
        if (wait.isZero()) {
            return;
        }
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public int observerCount() {
        return observers.size();
    }
}
