package edu.campusnexo.infrastructure.adapter.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.TrackedDetection;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * JSON published by the edge worker ({@code edge/yolo_edge_publisher.py} of the AI service repository) on
 * {@code ucc/pasto/bloque_a/piso_2/{space_id}/camara}. Also accepted over HTTP by
 * {@code POST /api/v1/twin/telemetry}. Bounding boxes use YOLO's normalized {@code xyxyn} format.
 * Classes other than person, laptop, backpack and book are ignored. {@code wifi_devices} is optional (the campus
 * Wi-Fi count of the room, when available). {@code emulated} is true only for frames that do not come from a real
 * camera. Unknown fields are ignored, so an older edge worker that still sends a {@code sensors} block is accepted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeTelemetryPayload(
        @JsonProperty("space_id") String spaceId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("camera_online") Boolean cameraOnline,
        @JsonProperty("detections") List<Detection> detections,
        @JsonProperty("wifi_devices") Integer wifiDevices,
        @JsonProperty("emulated") Boolean emulated) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detection(
            @JsonProperty("track_id") Long trackId,
            @JsonProperty("class") String objectClass,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("bbox") List<Double> bbox) {
    }

    /**
     * @param expectedSpaceId space taken from the MQTT topic or URL; it must match {@code space_id} when both are present
     * @param receivedAt      used when the payload has no timestamp
     */
    public TelemetrySnapshot toSnapshot(String expectedSpaceId, Instant receivedAt) {
        String effectiveSpace = spaceId == null || spaceId.isBlank() ? expectedSpaceId : spaceId;
        if (expectedSpaceId != null && !expectedSpaceId.equals(effectiveSpace)) {
            throw new DomainValidationException("space_id del mensaje (" + spaceId + ") no coincide con el tópico ("
                    + expectedSpaceId + ")");
        }
        boolean online = cameraOnline == null || cameraOnline;
        List<TrackedDetection> tracked = detections == null ? List.of() : detections.stream()
                .map(EdgeTelemetryPayload::toDetection)
                .filter(Objects::nonNull)
                .toList();
        return new TelemetrySnapshot(effectiveSpace, timestamp == null ? receivedAt : timestamp, online, tracked,
                wifiDevices, Boolean.TRUE.equals(emulated));
    }

    private static TrackedDetection toDetection(Detection detection) {
        if (detection == null || detection.objectClass() == null) {
            return null;
        }
        DetectedObjectClass type;
        try {
            type = DetectedObjectClass.valueOf(detection.objectClass().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unsupportedClass) {
            return null;
        }
        List<Double> box = detection.bbox();
        if (box == null || box.size() != 4 || box.contains(null)) {
            throw new DomainValidationException("bbox debe tener 4 valores [x_min, y_min, x_max, y_max] normalizados");
        }
        return new TrackedDetection(detection.trackId() == null ? 0 : detection.trackId(), type,
                detection.confidence() == null ? 1.0 : detection.confidence(),
                new BoundingBox(box.get(0), box.get(1), box.get(2), box.get(3)));
    }
}
