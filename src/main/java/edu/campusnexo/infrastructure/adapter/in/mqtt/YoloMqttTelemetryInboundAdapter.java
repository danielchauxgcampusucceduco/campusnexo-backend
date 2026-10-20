package edu.campusnexo.infrastructure.adapter.in.mqtt;

import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.infrastructure.adapter.contract.EdgeTelemetryPayload;
import edu.campusnexo.infrastructure.config.MqttSubscriptions;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound adapter for the edge vision worker (YOLO + ByteTrack). Subscribes to
 * {@code ucc/+/+/+/+/camara} (campus/branch/block/floor/space), translates the JSON frame into a
 * {@link TelemetrySnapshot} and calls {@link ProcessSpaceTelemetryUseCase}. The legacy channel name
 * {@code telemetria} is still accepted. Frames are processed on a single worker thread so that frames of
 * the same space keep their order without blocking the MQTT network thread. Malformed frames are logged
 * and discarded; they never stop the subscription.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class YoloMqttTelemetryInboundAdapter implements SmartLifecycle {
    static final String NAME = "MQTT · YoloMqttTelemetryInboundAdapter";
    private static final Logger log = LoggerFactory.getLogger(YoloMqttTelemetryInboundAdapter.class);

    private final MqttSubscriptions subscriptions;
    private final ProcessSpaceTelemetryUseCase useCase;
    private final JsonMapper json;
    private final Clock clock;
    private final String topicFilter;
    private final int qos;
    private ExecutorService worker;
    private volatile boolean running;

    YoloMqttTelemetryInboundAdapter(MqttSubscriptions subscriptions, ProcessSpaceTelemetryUseCase useCase,
                                    JsonMapper json, Clock clock,
                                    @Value("${app.mqtt.telemetry-topic-filter}") String topicFilter,
                                    @Value("${app.mqtt.qos}") int qos) {
        this.subscriptions = subscriptions;
        this.useCase = useCase;
        this.json = json;
        this.clock = clock;
        this.topicFilter = topicFilter;
        this.qos = qos;
    }

    @Override
    public void start() {
        worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mqtt-camara-", 0).factory());
        subscriptions.add(topicFilter, qos, (topic, message) -> worker.execute(() -> handle(topic, message)));
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        subscriptions.remove(topicFilter);
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void handle(String topic, MqttMessage message) {
        try {
            String spaceId = spaceIdFromTopic(topic);
            EdgeTelemetryPayload payload = json.readValue(message.getPayload(), EdgeTelemetryPayload.class);
            TelemetrySnapshot snapshot = payload.toSnapshot(spaceId, clock.instant());
            useCase.process(snapshot, NAME);
        } catch (RuntimeException failure) {
            log.warn("Frame descartado del tópico {}: {}", topic, failure.getMessage());
        }
    }

    /** {@code ucc/pasto/bloque_a/piso_2/{space_id}/camara} → {@code space_id}. */
    static String spaceIdFromTopic(String topic) {
        return MqttTopics.spaceId(topic, "camara", "telemetria");
    }
}
