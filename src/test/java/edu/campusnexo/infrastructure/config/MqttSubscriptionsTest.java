package edu.campusnexo.infrastructure.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MqttSubscriptionsTest {
    @Test
    void restoresRegisteredSubscriptionsAfterCleanSessionReconnect() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttSubscriptions subscriptions = new MqttSubscriptions(client);
        IMqttMessageListener listener = (topic, message) -> { };
        subscriptions.add("ucc/+/+/+/+/ambiente", 1, listener);
        ArgumentCaptor<MqttCallbackExtended> callback = ArgumentCaptor.forClass(MqttCallbackExtended.class);
        verify(client).setCallback(callback.capture());

        callback.getValue().connectComplete(true, "tcp://broker.example:1883");

        verify(client, times(2)).subscribe(eq("ucc/+/+/+/+/ambiente"), eq(1), any(IMqttMessageListener.class));
    }

    @Test
    void removesSubscriptionsFromBrokerAndRegistry() throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        MqttSubscriptions subscriptions = new MqttSubscriptions(client);
        subscriptions.add("ucc/+/+/+/+/ambiente", 1, (topic, message) -> { });

        subscriptions.remove("ucc/+/+/+/+/ambiente");

        verify(client).unsubscribe("ucc/+/+/+/+/ambiente");
    }
}
