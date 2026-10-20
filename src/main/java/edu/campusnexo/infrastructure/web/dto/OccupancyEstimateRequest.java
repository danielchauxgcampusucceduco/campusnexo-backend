package edu.campusnexo.infrastructure.web.dto;

import edu.campusnexo.application.port.in.EstimateOccupancyCommand;
import jakarta.validation.constraints.NotNull;

/** {@code yoloPeople} (camera count) and {@code outdoorCo2Ppm} are optional. */
public record OccupancyEstimateRequest(
        Integer yoloPeople,
        @NotNull Double co2Ppm,
        @NotNull Integer wifiDevices,
        Double outdoorCo2Ppm) {

    public EstimateOccupancyCommand toCommand(String spaceId) {
        return new EstimateOccupancyCommand(spaceId, yoloPeople, co2Ppm, wifiDevices, outdoorCo2Ppm);
    }
}
