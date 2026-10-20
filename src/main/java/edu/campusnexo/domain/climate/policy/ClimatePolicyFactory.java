package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;

/**
 * Abstract Factory: creates a family of thermal, ventilation and lighting strategies that are
 * designed to work together for one climate profile (for example, the tropical family cools,
 * dehumidifies, ventilates mechanically with windows closed and closes blinds to cut solar gain).
 */
public interface ClimatePolicyFactory {
    ClimateProfile climateProfile();

    ThermalPolicy createThermalPolicy();

    VentilationPolicy createVentilationPolicy();

    LightingPolicy createLightingPolicy();
}
