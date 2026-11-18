package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_DEVICES;

import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.TrackedDetection;
import java.time.Instant;
import java.util.List;

/**
 * One frame of the room's camera: the tracked detections of the edge device (YOLO + ByteTrack), projected to the
 * floor. When {@code cameraOnline} is false the detections are ignored by the occupancy filter. {@code wifiDevices}
 * is optional ({@code null} when the campus Wi-Fi does not report the room). {@code emulated} marks frames that do
 * not come from a real camera (a test source). No image leaves the edge device.
 */
public record TelemetrySnapshot(
        String spaceId,
        Instant capturedAt,
        boolean cameraOnline,
        List<TrackedDetection> detections,
        Integer wifiDevices,
        boolean emulated) {

    public TelemetrySnapshot {
        requireText(spaceId, "spaceId");
        requireNonNull(capturedAt, "capturedAt");
        detections = detections == null ? List.of() : List.copyOf(detections);
        if (wifiDevices != null) {
            requireRange(wifiDevices, 0, MAX_DEVICES, "wifiDevices");
        }
    }

    /** Frame from a real camera, without Wi-Fi data. */
    public TelemetrySnapshot(String spaceId, Instant capturedAt, boolean cameraOnline, List<TrackedDetection> detections) {
        this(spaceId, capturedAt, cameraOnline, detections, null, false);
    }

    /** People counted by the camera in this frame, or {@code null} when the camera is offline. */
    public Integer cameraPeopleCount() {
        if (!cameraOnline) {
            return null;
        }
        return (int) detections.stream().filter(d -> d.objectClass() == DetectedObjectClass.PERSON).count();
    }

    public int belongingsCount() {
        return (int) detections.stream().filter(d -> d.objectClass().isBelonging()).count();
    }
}
