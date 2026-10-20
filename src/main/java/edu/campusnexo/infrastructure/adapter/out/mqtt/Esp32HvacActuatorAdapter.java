package edu.campusnexo.infrastructure.adapter.out.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.twin.HvacCommand;
import edu.campusnexo.domain.twin.HvacControlState;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes HVAC commands for the room's ESP32 controller as retained QoS 1 messages, so a controller
 * that reconnects immediately receives the last command. The ESP32 firmware owns the electrical safety
 * interlocks; this adapter only states the desired strategy and setpoint.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class Esp32HvacActuatorAdapter implements HvacActuatorCommandPort {
    private static final Logger log = LoggerFactory.getLogger(Esp32HvacActuatorAdapter.class);

    private final MqttClient client;
    private final JsonMapper json;
    private final String topicTemplate;
    private final int qos;

    Esp32HvacActuatorAdapter(MqttClient client, JsonMapper json,
                             @Value("${app.mqtt.hvac-command-topic-template}") String topicTemplate,
                             @Value("${app.mqtt.qos}") int qos) {
        this.client = client;
        this.json = json;
        this.topicTemplate = topicTemplate;
        this.qos = qos;
    }

    @Override
    public void send(HvacCommand command) {
        String topic = RoomTopicTemplate.resolve(topicTemplate, command.spaceId());
        MqttMessage message = new MqttMessage(json.writeValueAsBytes(Esp32Command.from(command)));
        message.setQos(qos);
        message.setRetained(true);
        try {
            client.publish(topic, message);
        } catch (MqttException failure) {
            log.error("No se pudo publicar el comando HVAC {} en {}", command.commandId(), topic, failure);
        }
    }

    /** Wire format read by the ESP32 firmware (snake_case, flat). */
    record Esp32Command(
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("space_id") String spaceId,
            @JsonProperty("mode") HvacControlState mode,
            @JsonProperty("compressor_on") boolean compressorOn,
            @JsonProperty("setpoint_c") double setpointC,
            @JsonProperty("issued_at") Instant issuedAt,
            @JsonProperty("reason") String reason) {

        static Esp32Command from(HvacCommand command) {
            return new Esp32Command(command.commandId(), command.spaceId(), command.state(), command.compressorOn(),
                    command.setpointC(), command.issuedAt(), command.reason());
        }
    }
}
