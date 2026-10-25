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
 * Presence and range are both checked at the edge, so the client gets every wrong field at once
 * ({@code validation_failed}). The ranges are the domain's {@code MeasurementLimits} constants, not new
 * numbers, and the domain still checks them for any other entry point (MQTT, tests).
 */
public record TelemetryRequest(
        @NotBlank @Size(max = 32) String spaceId,
        Instant recordedAt,
        @NotNull @Min(0) @Max(MAX_PEOPLE) Integer peopleCount,
        @NotNull @Min(0) @Max(MAX_DEVICES) Integer objectCount,
        @NotNull @Min(0) @Max(MAX_DEVICES) Integer wifiDevices,
        @NotNull @DecimalMin("" + MIN_CO2_PPM) @DecimalMax("" + MAX_CO2_PPM) Double co2Ppm,
        @NotNull @DecimalMin("" + MIN_TEMPERATURE_C) @DecimalMax("" + MAX_TEMPERATURE_C) Double temperatureC,
        @NotNull @DecimalMin("" + MIN_HUMIDITY_PCT) @DecimalMax("" + MAX_HUMIDITY_PCT) Double humidityPct,
        @NotNull @DecimalMin("" + MIN_ILLUMINANCE_LUX) @DecimalMax("" + MAX_ILLUMINANCE_LUX) Double illuminanceLux,
        @NotNull @DecimalMin("" + MIN_POWER_KW) @DecimalMax("" + MAX_POWER_KW) Double activePowerKw) {

    public RecordTelemetryCommand toCommand() {
        return new RecordTelemetryCommand(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, co2Ppm,
                temperatureC, humidityPct, illuminanceLux, activePowerKw);
    }
}
