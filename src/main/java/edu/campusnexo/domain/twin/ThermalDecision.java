package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.climate.HvacMode;
import java.util.List;

/**
 * Output of the thermal controller: the strategy, what the unit should do in it, the setpoint and the
 * free-running temperature trajectory it predicted (one value per step, starting at the current temperature)
 * so the decision can be explained.
 */
public record ThermalDecision(
        HvacControlState state,
        HvacMode mode,
        double setpointC,
        int horizonMinutes,
        int stepMinutes,
        List<Double> freeRunningTrajectoryC,
        String explanation) {

    public ThermalDecision {
        requireNonNull(state, "state");
        requireNonNull(mode, "mode");
        require(state.compressorOn() == mode.isActive(),
                "El modo " + mode + " no corresponde a la estrategia " + state);
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
