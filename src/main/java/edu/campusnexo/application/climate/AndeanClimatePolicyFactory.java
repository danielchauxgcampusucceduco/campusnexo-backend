package edu.campusnexo.application.climate;

import edu.campusnexo.domain.model.ClimateProfile;
import edu.campusnexo.domain.policy.*;

public class AndeanClimatePolicyFactory implements ClimatePolicyFactory {
    public AndeanClimatePolicyFactory() {}
    @Override public ClimateProfile climateProfile() { return ClimateProfile.SEDE_ANDINA; }
    @Override public ThermalPolicy createThermalPolicy() { return new SetpointThermalPolicy(24.5); }
    @Override public VentilationPolicy createVentilationPolicy() { return new CarbonDioxideVentilationPolicy(1000); }
    @Override public LightingPolicy createLightingPolicy() { return new DaylightHarvestingPolicy(400, 450, 45); }
}
