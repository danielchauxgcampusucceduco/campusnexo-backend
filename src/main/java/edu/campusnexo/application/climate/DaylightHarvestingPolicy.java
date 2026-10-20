package edu.campusnexo.application.climate;

import edu.campusnexo.domain.policy.ClimateContext;
import edu.campusnexo.domain.policy.LightingDecision;
import edu.campusnexo.domain.policy.LightingPolicy;

final class DaylightHarvestingPolicy implements LightingPolicy {
    private final double targetLux;
    private final double daylightLux;
    private final int daylightLevelPct;
    DaylightHarvestingPolicy(double targetLux, double daylightLux, int daylightLevelPct) {
        this.targetLux = targetLux; this.daylightLux = daylightLux; this.daylightLevelPct = daylightLevelPct;
    }
    @Override public LightingDecision recommend(ClimateContext context) {
        if (context.occupancy() == 0) return new LightingDecision(0, "Sin ocupación; apagar iluminación cuando la operación lo permita");
        if (context.illuminanceLux() >= daylightLux) return new LightingDecision(daylightLevelPct,
                "Aprovechar luz natural; objetivo de referencia " + (int) targetLux + " lux");
        return new LightingDecision(100, "La iluminación medida está bajo el umbral de luz natural; mantener circuito disponible");
    }
}
