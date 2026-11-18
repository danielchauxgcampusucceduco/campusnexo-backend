package edu.campusnexo.infrastructure.adapter.in.web.dto;

import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_CO2_PPM;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_DEVICES;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_HUMIDITY_PCT;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_ILLUMINANCE_LUX;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_PEOPLE;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_POWER_KW;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_TEMPERATURE_C;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_CO2_PPM;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_HUMIDITY_PCT;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_ILLUMINANCE_LUX;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_POWER_KW;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_TEMPERATURE_C;

import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * A reading of a space posted directly (a counter or a test tool); camera frames go through the twin. Presence and
 * range are both checked at the edge, so the client gets every wrong field at once ({@code validation_failed}). The
 * ranges are the domain's {@code MeasurementLimits} constants, not new numbers, and the domain still checks them for
 * any other entry point (MQTT, tests).
 */
public record TelemetryRequest(
        @NotBlank @Size(max = 32) String spaceId,
        Instant recordedAt,
        @NotNull @Min(0) @Max(MAX_PEOPLE) Integer peopleCount,
        @NotNull @Min(0) @Max(MAX_DEVICES) Integer objectCount,
        @Min(0) @Max(MAX_DEVICES) Integer wifiDevices) {

    public RecordTelemetryCommand toCommand() {
        return new RecordTelemetryCommand(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, null);
    }
}
