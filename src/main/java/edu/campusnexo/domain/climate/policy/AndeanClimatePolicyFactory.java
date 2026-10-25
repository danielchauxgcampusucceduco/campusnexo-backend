package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ComfortBand;

/** Family for cold highland branches (for example Pasto): heating first, windows closed. */
public final class AndeanClimatePolicyFactory implements ClimatePolicyFactory {
    private static final ComfortBand BAND = new ComfortBand(19.0, 24.0);

    @Override
    public ClimateProfile climateProfile() {
        return ClimateProfile.SEDE_ANDINA;
    }

    @Override
    public ComfortBand comfortBand() {
        return BAND;
    }

    /** Heats below the band (to 20 °C) and only cools well above it (over 26 °C). */
    @Override
    public ThermalPolicy createThermalStrategy() {
        return new HeatingFirstThermalPolicy(BAND.lowC() + 1, BAND.highC() + 1, 1.0);
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
