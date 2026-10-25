package edu.campusnexo.infrastructure.adapter.in.mqtt;

/** Topic layout {@code ucc/{sede}/{bloque}/{piso}/{space_id}/{canal}}: the space is the second-to-last segment. */
final class MqttTopics {
    private static final int SPACE_SEGMENT_FROM_END = 2;

    private MqttTopics() {
    }

    /** {@code ucc/pasto/bloque_a/piso_2/201-A/camara} → {@code 201-A}, when the last segment is one of {@code channels}. */
    static String spaceId(String topic, String... channels) {
        String[] segments = topic.split("/");
        if (segments.length >= SPACE_SEGMENT_FROM_END) {
            String channel = segments[segments.length - 1];
            for (String accepted : channels) {
                if (accepted.equals(channel)) {
                    return segments[segments.length - SPACE_SEGMENT_FROM_END];
                }
            }
        }
        throw new IllegalArgumentException("Tópico no reconocido: " + topic);
    }
}
