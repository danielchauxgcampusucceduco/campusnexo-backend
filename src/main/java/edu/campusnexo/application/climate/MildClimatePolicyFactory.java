package edu.campusnexo.application.climate;

import edu.campusnexo.domain.model.ClimateProfile;
import edu.campusnexo.domain.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.policy.LightingPolicy;
import edu.campusnexo.domain.policy.ThermalPolicy;
import edu.campusnexo.domain.policy.VentilationPolicy;

public class MildClimatePolicyFactory implements ClimatePolicyFactory {
    public MildClimatePolicyFactory() {}

    @Override public ClimateProfile climateProfile() { return ClimateProfile.SEDE_TEMPLADA; }
    @Override public ThermalPolicy createThermalPolicy() { return new SetpointThermalPolicy(24.0); }
    @Override public VentilationPolicy createVentilationPolicy() { return new CarbonDioxideVentilationPolicy(1000); }
    @Override public LightingPolicy createLightingPolicy() { return new DaylightHarvestingPolicy(400, 450, 50); }
}
