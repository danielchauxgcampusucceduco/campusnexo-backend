package edu.campusnexo.infrastructure.adapter.in.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.port.out.OperationalMetrics;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

class MqttInboxTest {
    private final List<Integer> acknowledged = new CopyOnWriteArrayList<>();
    private final List<String> discarded = new CopyOnWriteArrayList<>();
    private final OperationalMetrics metrics = new OperationalMetrics() {
        @Override
        public void readingDiscarded(String source, String reason) {
            discarded.add(reason);
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
        public void frameLatency(java.time.Duration latency) {
        }
    };

    private static MqttMessage message(int id) {
        MqttMessage message = new MqttMessage(("m" + id).getBytes());
        message.setId(id);
        message.setQos(1);
        return message;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "el buzón no procesó a tiempo");
    }

    @Test
    void acknowledgesOnlyAfterProcessing() throws Exception {
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        try (MqttInbox inbox = new MqttInbox("camara", (topic, message) -> {
            processing.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            done.countDown();
        }, message -> acknowledged.add(message.getId()), metrics, 10, Duration.ZERO)) {
            inbox.offer("ucc/pasto/a/2/201-A/camara", message(1));
            await(processing);
            assertTrue(acknowledged.isEmpty(), "no se confirma mientras se procesa");
            release.countDown();
            await(done);
            Thread.sleep(50);
            assertEquals(List.of(1), acknowledged);
        }
    }

    @Test
    void keepsOnlyTheLatestWaitingMessagePerTopic() throws Exception {
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> processed = new CopyOnWriteArrayList<>();
        CountDownLatch twoProcessed = new CountDownLatch(2);
        try (MqttInbox inbox = new MqttInbox("camara", (topic, message) -> {
            busy.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            processed.add(new String(message.getPayload()));
            twoProcessed.countDown();
        }, message -> acknowledged.add(message.getId()), metrics, 10, Duration.ZERO)) {
            inbox.offer("sala/camara", message(1));
            await(busy);
            inbox.offer("sala/camara", message(2));
            inbox.offer("sala/camara", message(3));
            assertEquals(1, inbox.pendingCount(), "el atraso queda acotado a un mensaje por tópico");
            release.countDown();
            await(twoProcessed);
            assertEquals(List.of("m1", "m3"), processed);
            assertEquals(List.of("reemplazada"), discarded);
            assertTrue(acknowledged.contains(2), "el mensaje reemplazado también se confirma");
        }
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void retriesInfrastructureFailuresAndGivesUpOnRejectedReadings() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch finished = new CountDownLatch(2);
        try (MqttInbox inbox = new MqttInbox("arduino", (topic, message) -> {
            if (topic.endsWith("mala")) {
                finished.countDown();
                throw new MqttInbox.RejectedReading("JSON roto", null);
            }
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("base de datos caída");
            }
            finished.countDown();
        }, message -> acknowledged.add(message.getId()), metrics, 10, Duration.ofMillis(1))) {
            inbox.offer("sala/buena", message(1));
            inbox.offer("sala/mala", message(2));
            await(finished);
            // The metric and the acknowledgement come after the handler returns: wait for them, not a fixed time.
            awaitCondition(() -> discarded.size() == 1 && acknowledged.size() == 2);
            assertEquals(3, attempts.get());
            assertEquals(List.of("rechazada"), discarded);
            assertEquals(2, acknowledged.size());
        }
    }

    @Test
    void dropsNewTopicsBeyondTheBound() throws Exception {
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (MqttInbox inbox = new MqttInbox("camara", (topic, message) -> {
            busy.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, message -> acknowledged.add(message.getId()), metrics, 1, Duration.ZERO)) {
            inbox.offer("a/camara", message(1));
            await(busy);
            inbox.offer("b/camara", message(2));
            inbox.offer("c/camara", message(3));
            assertEquals(List.of("cola_llena"), discarded);
            assertTrue(acknowledged.contains(3));
            release.countDown();
        }
    }
}
