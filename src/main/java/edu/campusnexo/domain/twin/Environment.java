package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

/** Last known indoor and outdoor conditions of a room. */
public record Environment(double indoorTemperatureC, double outdoorTemperatureC, double co2Ppm, double humidityPct) {
    public Environment {
        require(Double.isFinite(indoorTemperatureC) && Double.isFinite(outdoorTemperatureC)
                && Double.isFinite(co2Ppm) && Double.isFinite(humidityPct), "Ambiente no válido");
    }
}
