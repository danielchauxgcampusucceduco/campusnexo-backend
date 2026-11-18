package edu.campusnexo.domain.telemetry;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_DEVICES;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_PEOPLE;

import java.time.Instant;

/**
 * Reading of a space recorded from its camera. {@code peopleCount} is the occupancy recorded for the room (the Kalman
 * estimate of the twin); {@code cameraPeople} is the camera's own count in that frame and is {@code null} when no
 * camera saw the room. Keeping both apart lets the AI service fuse raw measurements instead of an estimate that
 * already contains them. {@code objectCount} counts objects left on desks; {@code wifiDevices} is {@code null} when
 * the campus Wi-Fi does not report the room. No image or personal identifier is stored.
 */
public record Telemetry(
        String spaceId,
        Instant recordedAt,
        int peopleCount,
        int objectCount,
        Integer wifiDevices,
        Integer cameraPeople) {

    public Telemetry {
        requireText(spaceId, "spaceId");
        requireNonNull(recordedAt, "recordedAt");
        requireRange(peopleCount, 0, MAX_PEOPLE, "peopleCount");
        requireRange(objectCount, 0, MAX_DEVICES, "objectCount");
        if (wifiDevices != null) {
            requireRange(wifiDevices, 0, MAX_DEVICES, "wifiDevices");
        }
        if (cameraPeople != null) {
            requireRange(cameraPeople, 0, MAX_PEOPLE, "cameraPeople");
        }
    }

    /** Reading from a counter or tool that does not separate a camera count, without Wi-Fi data. */
    public Telemetry(String spaceId, Instant recordedAt, int peopleCount, int objectCount) {
        this(spaceId, recordedAt, peopleCount, objectCount, null, null);
    }

    public boolean isEmpty() {
        return peopleCount == 0;
    }
}
