package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

import edu.campusnexo.domain.catalog.ClimateProfile;

/** Indoor comfort band by climate profile (reference values for the prototype). */
public record ComfortBand(double lowC, double highC) {
    public ComfortBand {
        require(Double.isFinite(lowC) && Double.isFinite(highC) && lowC < highC, "Banda de confort no válida");
    }

    public static ComfortBand forProfile(ClimateProfile profile) {
        return switch (profile) {
            case SEDE_ANDINA -> new ComfortBand(19.0, 24.0);
            case SEDE_TROPICAL -> new ComfortBand(23.0, 26.0);
            case SEDE_TEMPLADA -> new ComfortBand(20.0, 25.0);
        };
    }

    public boolean contains(double temperatureC) {
        return temperatureC >= lowC && temperatureC <= highC;
    }

    public double midpoint() {
        return (lowC + highC) / 2;
    }
}
