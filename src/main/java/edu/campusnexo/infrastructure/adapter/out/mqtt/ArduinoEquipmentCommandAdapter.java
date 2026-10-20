package edu.campusnexo.infrastructure.adapter.out.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.campusnexo.domain.port.RoomEquipmentCommandPort;
import edu.campusnexo.domain.twin.EquipmentCommand;
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
 * Publishes lighting and computer shutdown orders for the room Arduino as QoS 1 messages (not retained:
 * an old shutdown must not switch off a room that is in use again when the board reconnects). The
 * firmware drives the lighting relay and sends the sleep signal to the computers.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class ArduinoEquipmentCommandAdapter implements RoomEquipmentCommandPort {
    private static final Logger log = LoggerFactory.getLogger(ArduinoEquipmentCommandAdapter.class);

    private final MqttClient client;
    private final JsonMapper json;
    private final String topicTemplate;
    private final int qos;

    ArduinoEquipmentCommandAdapter(MqttClient client, JsonMapper json,
                                   @Value("${app.mqtt.equipment-command-topic-template}") String topicTemplate,
                                   @Value("${app.mqtt.qos}") int qos) {
        this.client = client;
        this.json = json;
        this.topicTemplate = topicTemplate;
        this.qos = qos;
    }

    @Override
    public void send(EquipmentCommand command) {
        String topic = RoomTopicTemplate.resolve(topicTemplate, command.spaceId());
        MqttMessage message = new MqttMessage(json.writeValueAsBytes(ArduinoCommand.from(command)));
        message.setQos(qos);
        try {
            client.publish(topic, message);
        } catch (MqttException failure) {
            log.error("No se pudo publicar la orden de apagado {} en {}", command.commandId(), topic, failure);
        }
    }

    /** Wire format read by the Arduino firmware (snake_case, flat). */
    record ArduinoCommand(
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("space_id") String spaceId,
            @JsonProperty("lights_off") boolean lightsOff,
            @JsonProperty("suspend_devices") boolean suspendDevices,
            @JsonProperty("issued_at") Instant issuedAt,
            @JsonProperty("reason") String reason) {

        static ArduinoCommand from(EquipmentCommand command) {
            return new ArduinoCommand(command.commandId(), command.spaceId(), command.lightsOff(),
                    command.suspendDevices(), command.issuedAt(), command.reason());
        }
    }
}
