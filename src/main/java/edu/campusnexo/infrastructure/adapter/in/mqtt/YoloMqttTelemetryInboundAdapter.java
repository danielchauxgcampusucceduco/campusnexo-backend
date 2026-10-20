package edu.campusnexo.infrastructure.adapter.in.mqtt;

import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.infrastructure.adapter.contract.EdgeTelemetryPayload;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound adapter for the edge vision worker. Subscribes to
 * {@code universidad/+/+/+/+/telemetria} (campus/branch/block/floor/space), translates the JSON frame
 * into a {@link TelemetrySnapshot} and calls {@link ProcessSpaceTelemetryUseCase}. Frames are processed
 * on a single worker thread so that frames of the same space keep their order without blocking the
 * MQTT network thread. Malformed frames are logged and discarded; they never stop the subscription.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class YoloMqttTelemetryInboundAdapter implements SmartLifecycle {
    static final String NAME = "MQTT · YoloMqttTelemetryInboundAdapter";
    private static final Logger log = LoggerFactory.getLogger(YoloMqttTelemetryInboundAdapter.class);
    private static final int SPACE_SEGMENT_FROM_END = 2;

    private final MqttClient client;
    private final ProcessSpaceTelemetryUseCase useCase;
    private final JsonMapper json;
    private final Clock clock;
    private final String topicFilter;
    private final int qos;
    private ExecutorService worker;
    private volatile boolean running;

    YoloMqttTelemetryInboundAdapter(MqttClient client, ProcessSpaceTelemetryUseCase useCase, JsonMapper json, Clock clock,
                                    @Value("${app.mqtt.telemetry-topic-filter}") String topicFilter,
                                    @Value("${app.mqtt.qos}") int qos) {
        this.client = client;
        this.useCase = useCase;
        this.json = json;
        this.clock = clock;
        this.topicFilter = topicFilter;
        this.qos = qos;
    }

    @Override
    public void start() {
        worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mqtt-telemetry-", 0).factory());
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverUri) {
                if (reconnect) {
                    subscribe();
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("Conexión MQTT perdida; Paho reintentará: {}", cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // Messages are delivered to the per-subscription listener registered in subscribe().
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // This adapter only subscribes; deliveries belong to the actuator adapter.
            }
        });
        subscribe();
        running = true;
    }

    private void subscribe() {
        try {
            client.subscribe(topicFilter, qos, (topic, message) -> worker.execute(() -> handle(topic, message)));
            log.info("Suscrito a telemetría edge en {}", topicFilter);
        } catch (MqttException failure) {
            throw new IllegalStateException("No se pudo suscribir a " + topicFilter, failure);
        }
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (client.isConnected()) {
                client.unsubscribe(topicFilter);
            }
        } catch (MqttException failure) {
            log.warn("No se pudo cancelar la suscripción a {}", topicFilter, failure);
        }
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

    /** {@code universidad/sede_principal/bloque_b/piso_2/{space_id}/telemetria} → {@code space_id}. */
    static String spaceIdFromTopic(String topic) {
        String[] segments = topic.split("/");
        if (segments.length < SPACE_SEGMENT_FROM_END || !"telemetria".equals(segments[segments.length - 1])) {
            throw new IllegalArgumentException("Tópico de telemetría no reconocido: " + topic);
        }
        return segments[segments.length - SPACE_SEGMENT_FROM_END];
    }
}
