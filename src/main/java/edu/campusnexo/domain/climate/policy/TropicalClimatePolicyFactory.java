package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;

/** Family for hot humid branches (for example Barranquilla): cooling, dehumidification, glare control. */
public final class TropicalClimatePolicyFactory implements ClimatePolicyFactory {
    @Override
    public ClimateProfile climateProfile() {
        return ClimateProfile.SEDE_TROPICAL;
    }

    @Override
    public ThermalPolicy createThermalPolicy() {
        return new MinimumCycleThermalPolicy(new CoolingWithDehumidificationPolicy(25.5, 70, 0.5), 10, 5);
    }

    @Override
    public VentilationPolicy createVentilationPolicy() {
        return new Co2ThresholdVentilationPolicy(900, "ventanas cerradas mientras opera el aire acondicionado");
    }

    @Override
    public LightingPolicy createLightingPolicy() {
        return new GlareControlLightingPolicy(400, 1500, 40);
    }
}
