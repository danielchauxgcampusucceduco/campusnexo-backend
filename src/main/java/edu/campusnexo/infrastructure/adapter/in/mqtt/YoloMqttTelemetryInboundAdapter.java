package edu.campusnexo.infrastructure.adapter.in.mqtt;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.infrastructure.adapter.contract.EdgeTelemetryPayload;
import edu.campusnexo.infrastructure.config.MqttSubscriptions;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound adapter for the edge vision worker (YOLO + ByteTrack). Subscribes to
 * {@code ucc/+/+/+/+/camara} (campus/branch/block/floor/space), translates the JSON frame into a
 * {@link TelemetrySnapshot} and calls {@link ProcessSpaceTelemetryUseCase}. The legacy channel name
 * {@code telemetria} is still accepted. Frames go through an {@link MqttInbox}: acknowledged after they are
 * processed, at most one waiting per camera, malformed frames rejected and counted.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class YoloMqttTelemetryInboundAdapter implements SmartLifecycle {
    static final String NAME = "MQTT · " + YoloMqttTelemetryInboundAdapter.class.getSimpleName();

    private final MqttSubscriptions subscriptions;
    private final ProcessSpaceTelemetryUseCase useCase;
    private final JsonMapper json;
    private final Clock clock;
    private final OperationalMetrics metrics;
    private final String topicFilter;
    private final int qos;
    private final int maxPendingRooms;
    private MqttInbox inbox;
    private volatile boolean running;

    YoloMqttTelemetryInboundAdapter(MqttSubscriptions subscriptions, ProcessSpaceTelemetryUseCase useCase,
                                    JsonMapper json, Clock clock, OperationalMetrics metrics,
                                    @Value("${app.mqtt.telemetry-topic-filter}") String topicFilter,
                                    @Value("${app.mqtt.qos}") int qos,
                                    @Value("${app.mqtt.max-pending-rooms}") int maxPendingRooms) {
        this.subscriptions = subscriptions;
        this.useCase = useCase;
        this.json = json;
        this.clock = clock;
        this.metrics = metrics;
        this.topicFilter = topicFilter;
        this.qos = qos;
        this.maxPendingRooms = maxPendingRooms;
    }

    @Override
    public void start() {
        inbox = new MqttInbox("camara", this::handle, subscriptions::acknowledge, metrics, maxPendingRooms,
                Duration.ofMillis(200));
        subscriptions.add(topicFilter, qos, inbox::offer);
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        subscriptions.remove(topicFilter);
        inbox.close();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void handle(String topic, MqttMessage message) {
        TelemetrySnapshot snapshot;
        try {
            EdgeTelemetryPayload payload = json.readValue(message.getPayload(), EdgeTelemetryPayload.class);
            snapshot = payload.toSnapshot(spaceIdFromTopic(topic), clock.instant());
        } catch (JacksonException | IllegalArgumentException malformed) {
            throw new MqttInbox.RejectedReading("frame mal formado: " + malformed.getMessage(), malformed);
        }
        try {
            useCase.process(snapshot, NAME);
        } catch (DomainValidationException | ResourceNotFoundException invalid) {
            throw new MqttInbox.RejectedReading(invalid.getMessage(), invalid);
        }
    }

    /** {@code ucc/pasto/bloque_a/piso_2/{space_id}/camara} → {@code space_id}. */
    static String spaceIdFromTopic(String topic) {
        return MqttTopics.spaceId(topic, "camara", "telemetria");
    }
}
