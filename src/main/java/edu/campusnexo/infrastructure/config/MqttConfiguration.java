package edu.campusnexo.infrastructure.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared MQTT connection, created only when {@code app.mqtt.enabled=true}. It uses a persistent session
 * with a stable client id ({@code clean session = false}) and manual acknowledgements: the broker keeps the
 * QoS 1 messages that were not yet confirmed and delivers them again after a crash or a reconnection, and
 * each inbound adapter confirms a message only after processing it.
 */
@Configuration
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class MqttConfiguration {

    @Bean(destroyMethod = "close")
    ManagedMqttClient mqttClient(@Value("${app.mqtt.broker-url}") String brokerUrl,
                          @Value("${app.mqtt.client-id-prefix}") String clientIdPrefix,
                          @Value("${app.mqtt.instance-id}") String instanceId,
                          @Value("${app.mqtt.username:}") String username,
                          @Value("${app.mqtt.password:}") String password) throws MqttException {
        ManagedMqttClient client = new ManagedMqttClient(brokerUrl, clientId(clientIdPrefix, instanceId));
        client.setManualAcks(true);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        client.connect(options);
        return client;
    }

    /** Same id on every restart of an instance, so the broker finds its persistent session again. */
    static String clientId(String prefix, String instanceId) {
        String id = (prefix + "-" + instanceId).replaceAll("[^A-Za-z0-9_-]", "-");
        return id.length() > 64 ? id.substring(0, 64) : id;
    }

    /** Paho client that disconnects before closing, so the application shuts down cleanly. */
    static final class ManagedMqttClient extends MqttClient {
        ManagedMqttClient(String brokerUrl, String clientId) throws MqttException {
            super(brokerUrl, clientId, new MemoryPersistence());
        }

        @Override
        public void close() throws MqttException {
            if (isConnected()) {
                disconnect();
            }
            super.close();
        }
    }
}
