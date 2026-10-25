package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.LightingDecision;

/**
 * Hot sunny climate: very high illuminance means glare and solar heat gain, which increases the
 * cooling load. Blinds are closed (even when the room is empty) and light compensates.
 */
public final class GlareControlLightingPolicy implements LightingPolicy {
    private final double glareLux;
    private final int compensationLevelPct;
    private final DaylightHarvestingPolicy harvesting;

    public GlareControlLightingPolicy(double targetLux, double glareLux, int compensationLevelPct) {
        this.glareLux = glareLux;
        this.compensationLevelPct = compensationLevelPct;
        this.harvesting = new DaylightHarvestingPolicy(targetLux);
    }

    @Override
    public LightingDecision recommend(ClimateContext context) {
        boolean glare = context.illuminanceLux() > glareLux;
        if (!glare) {
            return harvesting.recommend(context);
        }
        if (!context.occupied()) {
            return new LightingDecision(0, true, "Radiación solar alta en espacio vacío; cerrar persianas para reducir carga térmica");
        }
        return new LightingDecision(compensationLevelPct, true,
                "Deslumbramiento sobre " + (int) glareLux + " lux; cerrar persianas y compensar con "
                        + compensationLevelPct + " % de iluminación");
    }
}
