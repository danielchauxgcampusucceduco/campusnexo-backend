package edu.campusnexo.infrastructure.config;

import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared MQTT connection, created only when {@code app.mqtt.enabled=true}. Subscriptions are restored by
 * the inbound adapter after an automatic reconnection (clean sessions do not keep them on the broker).
 */
@Configuration
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
class MqttConfiguration {

    @Bean(destroyMethod = "close")
    ManagedMqttClient mqttClient(@Value("${app.mqtt.broker-url}") String brokerUrl,
                          @Value("${app.mqtt.client-id-prefix}") String clientIdPrefix,
                          @Value("${app.mqtt.username:}") String username,
                          @Value("${app.mqtt.password:}") String password) throws MqttException {
        ManagedMqttClient client = new ManagedMqttClient(brokerUrl, clientIdPrefix + "-" + UUID.randomUUID());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        client.connect(options);
        return client;
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
