package edu.campusnexo.infrastructure.adapter.in.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MqttTopicsTest {
    @Test
    void extractsSpaceOnlyWhenTheFinalSegmentIsAnAcceptedChannel() {
        assertEquals("201-A", MqttTopics.spaceId("ucc/pasto/bloque_a/piso_2/201-A/camara", "camara", "ambiente"));
        assertEquals("201-A", MqttTopics.spaceId("ucc/pasto/bloque_a/piso_2/201-A/ambiente", "ambiente"));
    }

    @Test
    void rejectsUnknownAndTruncatedTopics() {
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.spaceId("ucc/pasto/201-A/camara", "ambiente"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.spaceId("ucc/pasto/bloque_a/piso_2/201-A/camera", "camara"));
    }
}
