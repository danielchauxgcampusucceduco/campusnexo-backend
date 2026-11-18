package edu.campusnexo.application.port.in;

import java.time.Instant;

/**
 * {@code recordedAt} is optional: the server clock is used when it is missing. {@code peopleCount} is the
 * occupancy of the room (the fused estimate when the reading comes from the twin); {@code cameraPeople} is
 * what the camera itself counted in that frame, {@code null} when no camera saw the room. {@code wifiDevices} is
 * optional too.
 */
public record RecordTelemetryCommand(
        String spaceId,
        Instant recordedAt,
        int peopleCount,
        int objectCount,
        Integer wifiDevices,
        Integer cameraPeople) {

    public RecordTelemetryCommand(String spaceId, Instant recordedAt, int peopleCount, int objectCount) {
        this(spaceId, recordedAt, peopleCount, objectCount, null, null);
    }
}
