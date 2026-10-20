package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.util.List;

/**
 * Output of the thermal controller, including the free-running temperature trajectory it predicted
 * (one value per step, starting at the current temperature) so the decision can be explained.
 */
public record ThermalDecision(
        HvacControlState state,
        double setpointC,
        int horizonMinutes,
        int stepMinutes,
        List<Double> freeRunningTrajectoryC,
        String explanation) {

    public ThermalDecision {
        requireNonNull(state, "state");
        requireNonNull(freeRunningTrajectoryC, "freeRunningTrajectoryC");
        requireText(explanation, "explanation");
        freeRunningTrajectoryC = List.copyOf(freeRunningTrajectoryC);
    }

    public double predictedMaxC() {
        return freeRunningTrajectoryC.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    public double predictedMinC() {
        return freeRunningTrajectoryC.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }
}
