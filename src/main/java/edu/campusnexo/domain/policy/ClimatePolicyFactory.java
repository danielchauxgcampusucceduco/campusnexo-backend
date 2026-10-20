package edu.campusnexo.domain.policy;

import edu.campusnexo.domain.model.ClimateProfile;

/** Abstract Factory for a compatible family of climate-specific policy strategies. */
public interface ClimatePolicyFactory {
    ClimateProfile climateProfile();
    ThermalPolicy createThermalPolicy();
    VentilationPolicy createVentilationPolicy();
    LightingPolicy createLightingPolicy();
}
