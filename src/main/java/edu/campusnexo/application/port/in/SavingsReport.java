package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.twin.HvacControlState;
import java.time.Instant;
import java.util.List;

/**
 * Energy saved against the conventional schedule baseline. Estimates from the prototype's model,
 * not metered values; the emission factor is configurable.
 */
public record SavingsReport(
        Instant generatedAt,
        double totalKwh,
        double totalCop,
        double totalKgCo2,
        double emissionFactorKgCo2PerKwh,
        List<SpaceSavings> spaces) {

    public record SpaceSavings(String spaceId, String name, String branchName, HvacControlState hvacState,
                               double ratedKw, double kwh, double cop, double kgCo2) {
    }
}
