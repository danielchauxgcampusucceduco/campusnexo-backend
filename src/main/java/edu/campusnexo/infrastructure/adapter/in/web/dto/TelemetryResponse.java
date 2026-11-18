package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;

/** Field names are kept stable because the React dashboard reads them. */
public record TelemetryResponse(
        String spaceId,
        Instant recordedAt,
        int peopleCount,
        int objectCount,
        Integer wifiDevices,
        Integer cameraPeople) {

    public static TelemetryResponse from(Telemetry telemetry) {
        return new TelemetryResponse(telemetry.spaceId(), telemetry.recordedAt(), telemetry.peopleCount(),
                telemetry.objectCount(), telemetry.wifiDevices(), telemetry.cameraPeople());
    }
}
