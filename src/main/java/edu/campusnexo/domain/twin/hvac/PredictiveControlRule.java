package edu.campusnexo.domain.twin.hvac;

import edu.campusnexo.domain.twin.ThermalDecision;

/** End of the chain: the strategy proposed by the predictive (thermal inertia) controller. */
final class PredictiveControlRule implements HvacSettingRule {
    @Override
    public HvacResolution resolve(HvacRequest request, HvacSettingChain next) {
        ThermalDecision predicted = request.predicted();
        return new HvacResolution(predicted.state(), predicted.mode(), predicted.setpointC(), predicted.explanation(),
                ruleName());
    }
}
