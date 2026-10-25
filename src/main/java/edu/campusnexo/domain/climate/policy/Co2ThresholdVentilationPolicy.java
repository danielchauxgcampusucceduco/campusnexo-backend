package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.VentilationDecision;
import edu.campusnexo.domain.climate.VentilationMode;

/** Mechanical ventilation driven only by CO2, for buildings that keep windows closed. */
public final class Co2ThresholdVentilationPolicy implements VentilationPolicy {
    private final double thresholdPpm;
    private final String closedWindowsReason;

    public Co2ThresholdVentilationPolicy(double thresholdPpm, String closedWindowsReason) {
        this.thresholdPpm = thresholdPpm;
        this.closedWindowsReason = closedWindowsReason;
    }

    @Override
    public VentilationDecision recommend(ClimateContext context) {
        if (context.co2Ppm() > thresholdPpm) {
            return new VentilationDecision(VentilationMode.MECANICA,
                    "CO2 sobre " + (int) thresholdPpm + " ppm; renovación mecánica de aire (" + closedWindowsReason + ")");
        }
        return new VentilationDecision(VentilationMode.NINGUNA, "CO2 bajo el umbral de referencia");
    }
}
