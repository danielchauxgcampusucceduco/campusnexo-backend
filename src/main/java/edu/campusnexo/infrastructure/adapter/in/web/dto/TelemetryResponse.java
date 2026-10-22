package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;

/** Field names are kept stable because the React dashboard reads them. */
public record TelemetryResponse(
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

    public static TelemetryResponse from(Telemetry telemetry) {
        return new TelemetryResponse(telemetry.spaceId(), telemetry.recordedAt(), telemetry.peopleCount(),
                telemetry.objectCount(), telemetry.wifiDevices(), telemetry.co2Ppm(), telemetry.temperatureC(),
                telemetry.humidityPct(), telemetry.illuminanceLux(), telemetry.activePowerKw(), telemetry.cameraPeople());
    }
}
