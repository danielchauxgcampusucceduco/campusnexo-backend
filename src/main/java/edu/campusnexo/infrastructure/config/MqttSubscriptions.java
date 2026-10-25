package edu.campusnexo.infrastructure.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Owns the single Paho callback of the shared connection. Inbound adapters register their topic filter
 * here instead of setting their own callback (Paho keeps only one), every registered subscription is
 * restored after an automatic reconnection, and the adapters confirm each message here once they have
 * processed it (the client runs with manual acknowledgements).
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
public class MqttSubscriptions {
    private static final Logger log = LoggerFactory.getLogger(MqttSubscriptions.class);

    private final MqttClient client;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    MqttSubscriptions(MqttClient client) {
        this.client = client;
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverUri) {
                if (reconnect) {
                    subscriptions.forEach(MqttSubscriptions.this::subscribe);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("Conexión MQTT perdida; Paho reintentará: {}", cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // Messages are delivered to the per-subscription listeners.
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Publications belong to the outbound adapters.
            }
        });
    }

    public void add(String topicFilter, int qos, IMqttMessageListener listener) {
        Subscription subscription = new Subscription(topicFilter, qos, listener);
        subscriptions.add(subscription);
        subscribe(subscription);
    }

    /** Confirms a QoS 1 message to the broker; until then the broker keeps it for redelivery. */
    public void acknowledge(MqttMessage message) {
        if (message.getQos() == 0) {
            return;
        }
        try {
            client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (MqttException failure) {
            log.warn("No se pudo confirmar el mensaje MQTT {}", message.getId(), failure);
        }
    }

    public void remove(String topicFilter) {
        subscriptions.removeIf(s -> s.topicFilter().equals(topicFilter));
        try {
            if (client.isConnected()) {
                client.unsubscribe(topicFilter);
            }
        } catch (MqttException failure) {
            log.warn("No se pudo cancelar la suscripción a {}", topicFilter, failure);
        }
    }

    private void subscribe(Subscription subscription) {
        try {
            client.subscribe(subscription.topicFilter(), subscription.qos(), subscription.listener());
            log.info("Suscrito a {}", subscription.topicFilter());
        } catch (MqttException failure) {
            throw new IllegalStateException("No se pudo suscribir a " + subscription.topicFilter(), failure);
        }
    }

    private record Subscription(String topicFilter, int qos, IMqttMessageListener listener) {
    }
}
