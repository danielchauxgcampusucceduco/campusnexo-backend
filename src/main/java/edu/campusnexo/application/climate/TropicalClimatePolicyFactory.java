package edu.campusnexo.application.climate;

import edu.campusnexo.domain.model.ClimateProfile;
import edu.campusnexo.domain.policy.*;

public class TropicalClimatePolicyFactory implements ClimatePolicyFactory {
    public TropicalClimatePolicyFactory() {}
    @Override public ClimateProfile climateProfile() { return ClimateProfile.SEDE_TROPICAL; }
    @Override public ThermalPolicy createThermalPolicy() { return new SetpointThermalPolicy(26.0); }
    @Override public VentilationPolicy createVentilationPolicy() { return new CarbonDioxideVentilationPolicy(1000); }
    @Override public LightingPolicy createLightingPolicy() { return new DaylightHarvestingPolicy(400, 450, 60); }
}
