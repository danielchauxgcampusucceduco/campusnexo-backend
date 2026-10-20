package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;

/**
 * Inputs of the thermal controller. {@code minutesUntilOccupancyEnds} and
 * {@code minutesUntilNextOccupancy} come from the reservations and may be {@code null}.
 */
public record ThermalContext(
        double indoorTemperatureC,
        double outdoorTemperatureC,
        int occupants,
        double areaSqm,
        double co2Ppm,
        ComfortBand comfortBand,
        Integer minutesUntilOccupancyEnds,
        Integer minutesUntilNextOccupancy) {

    public ThermalContext {
        require(Double.isFinite(indoorTemperatureC) && Double.isFinite(outdoorTemperatureC), "Temperaturas no válidas");
        require(occupants >= 0 && areaSqm > 0 && co2Ppm >= 0, "Contexto térmico no válido");
        requireNonNull(comfortBand, "comfortBand");
        require(minutesUntilOccupancyEnds == null || minutesUntilOccupancyEnds >= 0, "minutesUntilOccupancyEnds negativo");
        require(minutesUntilNextOccupancy == null || minutesUntilNextOccupancy >= 0, "minutesUntilNextOccupancy negativo");
    }

    public boolean occupied() {
        return occupants > 0;
    }
}
