package edu.campusnexo.application.port.in;

import java.time.Instant;

/**
 * {@code recordedAt} is optional: the server clock is used when it is missing. {@code peopleCount} is the
 * occupancy of the room (the fused estimate when the reading comes from the twin); {@code cameraPeople} is
 * what the camera itself counted in that frame, {@code null} when no camera saw the room.
 */
public record RecordTelemetryCommand(
        String spaceId,
        Instant recordedAt,
        int peopleCount,
        int objectCount,
        int wifiDevices,
        double co2Ppm,
        double temperatureC,
        double humidityPct,
        double illuminanceLux,
        double activePowerKw,
        Integer cameraPeople) {

    public RecordTelemetryCommand(String spaceId, Instant recordedAt, int peopleCount, int objectCount, int wifiDevices,
                                  double co2Ppm, double temperatureC, double humidityPct, double illuminanceLux,
                                  double activePowerKw) {
        this(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, co2Ppm, temperatureC, humidityPct,
                illuminanceLux, activePowerKw, null);
    }
}
