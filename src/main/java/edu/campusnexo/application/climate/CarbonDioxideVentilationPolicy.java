package edu.campusnexo.application.climate;

import edu.campusnexo.domain.policy.ClimateContext;
import edu.campusnexo.domain.policy.VentilationDecision;
import edu.campusnexo.domain.policy.VentilationPolicy;

final class CarbonDioxideVentilationPolicy implements VentilationPolicy {
    private final double thresholdPpm;
    CarbonDioxideVentilationPolicy(double thresholdPpm) { this.thresholdPpm = thresholdPpm; }
    @Override public VentilationDecision recommend(ClimateContext context) {
        boolean enabled = context.co2Ppm() > thresholdPpm;
        return new VentilationDecision(enabled, enabled
                ? "CO2 supera el umbral operativo de referencia; revisar renovación de aire"
                : "CO2 está bajo el umbral operativo de referencia");
    }
}
