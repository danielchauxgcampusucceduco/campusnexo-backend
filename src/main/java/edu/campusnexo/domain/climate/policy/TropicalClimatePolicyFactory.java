package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ComfortBand;

/** Family for hot humid branches (for example Barranquilla): cooling, dehumidification, glare control. */
public final class TropicalClimatePolicyFactory implements ClimatePolicyFactory {
    private static final ComfortBand BAND = new ComfortBand(23.0, 26.0);

    @Override
    public ClimateProfile climateProfile() {
        return ClimateProfile.SEDE_TROPICAL;
    }

    @Override
    public ComfortBand comfortBand() {
        return BAND;
    }

    /** Cools above the band (setpoint 25.5 °C) and dehumidifies over 70 % relative humidity. */
    @Override
    public ThermalPolicy createThermalStrategy() {
        return new CoolingWithDehumidificationPolicy(BAND.highC() - 0.5, 70, 0.5);
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
