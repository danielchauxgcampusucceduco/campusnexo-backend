package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ComfortBand;

/** Family for mild branches (for example Medellín): wide comfort band and natural ventilation. */
public final class MildClimatePolicyFactory implements ClimatePolicyFactory {
    private static final ComfortBand BAND = new ComfortBand(20.0, 25.0);

    @Override
    public ClimateProfile climateProfile() {
        return ClimateProfile.SEDE_TEMPLADA;
    }

    @Override
    public ComfortBand comfortBand() {
        return BAND;
    }

    /** HVAC only outside the band, preferring free cooling when the outside air is 2 °C cooler. */
    @Override
    public ThermalPolicy createThermalStrategy() {
        return new ComfortBandThermalPolicy(BAND.lowC(), BAND.highC(), 2.0);
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
