package edu.campusnexo.application.climate;

import edu.campusnexo.domain.model.ClimateProfile;
import edu.campusnexo.domain.policy.*;

public class TemplatedClimatePolicyFactory implements ClimatePolicyFactory {
    public TemplatedClimatePolicyFactory() {}
    @Override public ClimateProfile climateProfile() { return ClimateProfile.SEDE_TEMPLADA; }
    @Override public ThermalPolicy createThermalPolicy() { return new SetpointThermalPolicy(24.0); }
    @Override public VentilationPolicy createVentilationPolicy() { return new CarbonDioxideVentilationPolicy(1000); }
    @Override public LightingPolicy createLightingPolicy() { return new DaylightHarvestingPolicy(400, 450, 50); }
}
