package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.LightingDecision;

/** Dims artificial light in proportion to the daylight that is missing to reach the target. */
public final class DaylightHarvestingPolicy implements LightingPolicy {
    private final double targetLux;

    public DaylightHarvestingPolicy(double targetLux) {
        this.targetLux = targetLux;
    }

    @Override
    public LightingDecision recommend(ClimateContext context) {
        if (!context.occupied()) {
            return new LightingDecision(0, false, "Sin ocupación; apagar iluminación");
        }
        double missing = Math.max(0, targetLux - context.illuminanceLux());
        int level = (int) Math.min(100, Math.round(missing / targetLux * 100));
        if (level == 0) {
            return new LightingDecision(0, false,
                    "La luz natural alcanza el objetivo de " + (int) targetLux + " lux; iluminación artificial apagada");
        }
        return new LightingDecision(level, false,
                "Complementar la luz natural hasta " + (int) targetLux + " lux con " + level + " % de iluminación");
    }
}
