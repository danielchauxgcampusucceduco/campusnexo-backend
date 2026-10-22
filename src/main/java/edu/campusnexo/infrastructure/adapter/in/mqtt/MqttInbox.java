package edu.campusnexo.infrastructure.adapter.in.mqtt;

import edu.campusnexo.application.port.out.OperationalMetrics;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded inbox between the MQTT network thread and a use case, with at-least-once processing.
 *
 * <ul>
 *   <li><b>Acknowledged after processing.</b> Paho runs with manual acknowledgements: a message is
 *       confirmed to the broker only once the use case has handled it, so a crash in between makes the broker
 *       deliver it again (the twin then ignores the duplicate by its capture time).</li>
 *   <li><b>Latest message per topic.</b> Each topic is one room's device, and every message is a full
 *       snapshot of it; if a newer one arrives while the previous one is still waiting, the older one is
 *       acknowledged as superseded and dropped. The backlog is therefore bounded by the number of rooms, not
 *       by how fast the broker sends.</li>
 *   <li><b>Rejections are final, failures are retried.</b> A malformed or invalid reading is acknowledged and
 *       counted; an infrastructure failure (database, write conflict) is retried with a growing pause and,
 *       if it keeps failing, logged as an error and counted.</li>
 * </ul>
 */
final class MqttInbox implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MqttInbox.class);
    static final int MAX_ATTEMPTS = 3;

    /** Processes one message; throws {@link RejectedReading} when retrying cannot help. */
    @FunctionalInterface
    interface Handler {
        void handle(String topic, MqttMessage message);
    }

    /** Confirms a message to the broker. */
    @FunctionalInterface
    interface Acknowledger {
        void acknowledge(MqttMessage message);
    }

    /** A reading that is malformed or invalid: processing it again would fail again. */
    static final class RejectedReading extends RuntimeException {
        RejectedReading(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record Delivery(String topic, MqttMessage message) {
    }

    private final String source;
    private final Handler handler;
    private final Acknowledger acknowledger;
    private final OperationalMetrics metrics;
    private final int maxTopics;
    private final Duration retryPause;
    private final Map<String, Delivery> pending = new LinkedHashMap<>();
    private final Thread worker;
    private boolean open = true;

    MqttInbox(String source, Handler handler, Acknowledger acknowledger, OperationalMetrics metrics, int maxTopics,
              Duration retryPause) {
        this.source = source;
        this.handler = handler;
        this.acknowledger = acknowledger;
        this.metrics = metrics;
        this.maxTopics = maxTopics;
        this.retryPause = retryPause;
        this.worker = Thread.ofVirtual().name("mqtt-" + source).start(this::drain);
    }

    /** Called on the MQTT network thread: never blocks on processing. */
    void offer(String topic, MqttMessage message) {
        Delivery superseded;
        synchronized (this) {
            if (!open) {
                return;
            }
            superseded = pending.remove(topic);
            if (superseded == null && pending.size() >= maxTopics) {
                discard(new Delivery(topic, message), "cola_llena");
                return;
            }
            pending.put(topic, new Delivery(topic, message));
            notifyAll();
        }
        if (superseded != null) {
            discard(superseded, "reemplazada");
        }
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    private void drain() {
        while (true) {
            Delivery next;
            synchronized (this) {
                while (open && pending.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!open && pending.isEmpty()) {
                    return;
                }
                Iterator<Delivery> oldest = pending.values().iterator();
                next = oldest.next();
                oldest.remove();
            }
            process(next);
        }
    }

    private void process(Delivery delivery) {
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    handler.handle(delivery.topic(), delivery.message());
                    return;
                } catch (RejectedReading rejected) {
                    log.warn("Lectura rechazada del tópico {}: {}", delivery.topic(), rejected.getMessage());
                    metrics.readingDiscarded(source, "rechazada");
                    return;
                } catch (RuntimeException failure) {
                    if (attempt >= MAX_ATTEMPTS) {
                        log.error("Lectura del tópico {} no procesada tras {} intentos", delivery.topic(), attempt, failure);
                        metrics.readingDiscarded(source, "fallida");
                        return;
                    }
                    pause(retryPause.multipliedBy(attempt));
                }
            }
        } finally {
            acknowledge(delivery.message());
        }
    }

    private void discard(Delivery delivery, String reason) {
        metrics.readingDiscarded(source, reason);
        acknowledge(delivery.message());
    }

    private void acknowledge(MqttMessage message) {
        try {
            acknowledger.acknowledge(message);
        } catch (RuntimeException failure) {
            log.warn("No se pudo confirmar el mensaje MQTT {}: {}", message.getId(), failure.getMessage());
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Stops accepting messages, lets the worker finish what is pending (up to {@code timeout}). */
    void close(Duration timeout) {
        synchronized (this) {
            open = false;
            notifyAll();
        }
        try {
            worker.join(timeout.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        worker.interrupt();
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(5));
    }
}
