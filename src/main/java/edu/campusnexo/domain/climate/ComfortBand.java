package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.require;

/**
 * Indoor temperature band in which a room is comfortable. Each climate family defines its own band
 * ({@code ClimatePolicyFactory#comfortBand()}); the predictive controller and the thermal strategies of
 * that family read the same values, so the campus has a single definition of comfort per climate.
 */
public record ComfortBand(double lowC, double highC) {
    public ComfortBand {
        require(Double.isFinite(lowC) && Double.isFinite(highC) && lowC < highC, "Banda de confort no válida");
    }

    public boolean contains(double temperatureC) {
        return temperatureC >= lowC && temperatureC <= highC;
    }

    public double midpoint() {
        return (lowC + highC) / 2;
    }
}
