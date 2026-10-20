package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;

/** Family for mild branches (for example Medellín): wide comfort band and natural ventilation. */
public final class MildClimatePolicyFactory implements ClimatePolicyFactory {
    @Override
    public ClimateProfile climateProfile() {
        return ClimateProfile.SEDE_TEMPLADA;
    }

    @Override
    public ThermalPolicy createThermalPolicy() {
        return new MinimumCycleThermalPolicy(new ComfortBandThermalPolicy(20.0, 25.0, 2.0), 10, 5);
    }

    @Override
    public VentilationPolicy createVentilationPolicy() {
        return new NaturalVentilationPolicy(1000, 16, 27, 25);
    }

    @Override
    public LightingPolicy createLightingPolicy() {
        return new DaylightHarvestingPolicy(400);
    }
}
