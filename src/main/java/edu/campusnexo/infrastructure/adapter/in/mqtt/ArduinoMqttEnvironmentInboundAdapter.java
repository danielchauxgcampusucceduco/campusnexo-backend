package edu.campusnexo.infrastructure.adapter.in.mqtt;

import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.infrastructure.adapter.contract.ArduinoEnvironmentPayload;
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
 * Inbound adapter for the room Arduino. Subscribes to {@code ucc/+/+/+/+/ambiente}, translates the JSON
 * reading (temperature, humidity, light, lighting relay, computers on) into an {@link EnvironmentReading}
 * and calls {@link ProcessEnvironmentReadingUseCase}. Readings are processed in order on one worker thread;
 * malformed readings are logged and discarded.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class ArduinoMqttEnvironmentInboundAdapter implements SmartLifecycle {
    static final String NAME = "MQTT · ArduinoMqttEnvironmentInboundAdapter";
    private static final Logger log = LoggerFactory.getLogger(ArduinoMqttEnvironmentInboundAdapter.class);

    private final MqttSubscriptions subscriptions;
    private final ProcessEnvironmentReadingUseCase useCase;
    private final JsonMapper json;
    private final Clock clock;
    private final String topicFilter;
    private final int qos;
    private ExecutorService worker;
    private volatile boolean running;

    ArduinoMqttEnvironmentInboundAdapter(MqttSubscriptions subscriptions, ProcessEnvironmentReadingUseCase useCase,
                                         JsonMapper json, Clock clock,
                                         @Value("${app.mqtt.environment-topic-filter}") String topicFilter,
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
        worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mqtt-ambiente-", 0).factory());
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
            String spaceId = MqttTopics.spaceId(topic, "ambiente");
            ArduinoEnvironmentPayload payload = json.readValue(message.getPayload(), ArduinoEnvironmentPayload.class);
            useCase.processEnvironment(payload.toReading(spaceId, clock.instant()), NAME);
        } catch (RuntimeException failure) {
            log.warn("Lectura del Arduino descartada del tópico {}: {}", topic, failure.getMessage());
        }
    }
}
