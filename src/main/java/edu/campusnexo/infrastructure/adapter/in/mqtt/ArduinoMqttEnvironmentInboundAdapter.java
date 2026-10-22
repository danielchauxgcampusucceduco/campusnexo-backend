package edu.campusnexo.infrastructure.adapter.in.mqtt;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.infrastructure.adapter.contract.ArduinoEnvironmentPayload;
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
 * Inbound adapter for the room Arduino (DHT22, light sensor, lighting relay, current sensor). Subscribes to
 * {@code ucc/+/+/+/+/ambiente}, translates the JSON into an {@link EnvironmentReading} and calls
 * {@link ProcessEnvironmentReadingUseCase}. Readings go through an {@link MqttInbox}: acknowledged after they
 * are processed, at most one waiting per board, malformed readings rejected and counted.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class ArduinoMqttEnvironmentInboundAdapter implements SmartLifecycle {
    static final String NAME = "MQTT · " + ArduinoMqttEnvironmentInboundAdapter.class.getSimpleName();

    private final MqttSubscriptions subscriptions;
    private final ProcessEnvironmentReadingUseCase useCase;
    private final JsonMapper json;
    private final Clock clock;
    private final OperationalMetrics metrics;
    private final String topicFilter;
    private final int qos;
    private final int maxPendingRooms;
    private MqttInbox inbox;
    private volatile boolean running;

    ArduinoMqttEnvironmentInboundAdapter(MqttSubscriptions subscriptions, ProcessEnvironmentReadingUseCase useCase,
                                         JsonMapper json, Clock clock, OperationalMetrics metrics,
                                         @Value("${app.mqtt.environment-topic-filter}") String topicFilter,
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
        inbox = new MqttInbox("arduino", this::handle, subscriptions::acknowledge, metrics, maxPendingRooms,
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
        EnvironmentReading reading;
        try {
            ArduinoEnvironmentPayload payload = json.readValue(message.getPayload(), ArduinoEnvironmentPayload.class);
            reading = payload.toReading(MqttTopics.spaceId(topic, "ambiente"), clock.instant());
        } catch (JacksonException | IllegalArgumentException malformed) {
            throw new MqttInbox.RejectedReading("lectura mal formada: " + malformed.getMessage(), malformed);
        }
        try {
            useCase.processEnvironment(reading, NAME);
        } catch (DomainValidationException | ResourceNotFoundException invalid) {
            throw new MqttInbox.RejectedReading(invalid.getMessage(), invalid);
        }
    }
}
