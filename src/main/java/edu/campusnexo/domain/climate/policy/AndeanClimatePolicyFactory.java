package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;

/** Family for cold highland branches (for example Pasto): heating first, windows closed. */
public final class AndeanClimatePolicyFactory implements ClimatePolicyFactory {
    @Override
    public ClimateProfile climateProfile() {
        return ClimateProfile.SEDE_ANDINA;
    }

    @Override
    public ThermalPolicy createThermalPolicy() {
        return new MinimumCycleThermalPolicy(new HeatingFirstThermalPolicy(20.0, 25.0, 1.0), 10, 5);
    }

    @Override
    public VentilationPolicy createVentilationPolicy() {
        return new Co2ThresholdVentilationPolicy(1000, "ventanas cerradas para conservar el calor");
    }

    @Override
    public LightingPolicy createLightingPolicy() {
        return new DaylightHarvestingPolicy(400);
    }
}
