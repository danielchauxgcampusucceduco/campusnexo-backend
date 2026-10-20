package edu.campusnexo.application.port.in;

import java.time.Instant;

/** {@code recordedAt} is optional: the server clock is used when it is missing. */
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
        double activePowerKw) {
}
