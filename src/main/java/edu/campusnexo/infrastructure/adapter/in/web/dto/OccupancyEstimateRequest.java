package edu.campusnexo.infrastructure.adapter.in.web.dto;

import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_CO2_PPM;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_DEVICES;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_OUTDOOR_CO2_PPM;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_PEOPLE;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_CO2_PPM;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_OUTDOOR_CO2_PPM;

import edu.campusnexo.application.port.in.EstimateOccupancyCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code yoloPeople} (camera count) and {@code outdoorCo2Ppm} are optional; ranges come from the domain limits. */
public record OccupancyEstimateRequest(
        @Min(0) @Max(MAX_PEOPLE) Integer yoloPeople,
        @NotNull @DecimalMin("" + MIN_CO2_PPM) @DecimalMax("" + MAX_CO2_PPM) Double co2Ppm,
        @NotNull @Min(0) @Max(MAX_DEVICES) Integer wifiDevices,
        @DecimalMin("" + MIN_OUTDOOR_CO2_PPM) @DecimalMax("" + MAX_OUTDOOR_CO2_PPM) Double outdoorCo2Ppm) {

    public EstimateOccupancyCommand toCommand(String spaceId) {
        return new EstimateOccupancyCommand(spaceId, yoloPeople, co2Ppm, wifiDevices, outdoorCo2Ppm);
    }
}
