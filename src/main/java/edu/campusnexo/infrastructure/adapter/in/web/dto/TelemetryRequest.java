package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Bean Validation checks presence only; value ranges are enforced once, in the domain. */
public record TelemetryRequest(
        @NotBlank @Size(max = 32) String spaceId,
        Instant recordedAt,
        @NotNull Integer peopleCount,
        @NotNull Integer objectCount,
        @NotNull Integer wifiDevices,
        @NotNull Double co2Ppm,
        @NotNull Double temperatureC,
        @NotNull Double humidityPct,
        @NotNull Double illuminanceLux,
        @NotNull Double activePowerKw) {

    public RecordTelemetryCommand toCommand() {
        return new RecordTelemetryCommand(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, co2Ppm,
                temperatureC, humidityPct, illuminanceLux, activePowerKw);
    }
}
