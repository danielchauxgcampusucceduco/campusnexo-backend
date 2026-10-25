package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ComfortBand;
import edu.campusnexo.domain.climate.CompressorGuard;

/**
 * Abstract Factory: creates a family of climate products designed to work together for one climate
 * profile. A family defines what "comfortable" means there ({@link #comfortBand()}), how the compressor is
 * protected, and the thermal, ventilation and lighting strategies. The tropical family, for example,
 * cools, dehumidifies, ventilates mechanically with windows closed and closes blinds against the sun.
 *
 * <p>Both deciders of the campus read the same family: the climate recommendation and the digital twin,
 * whose predictive controller works over {@link #comfortBand()} and whose HVAC mode comes from
 * {@link #createThermalStrategy()}. Mixing products of two families is therefore impossible.
 */
public interface ClimatePolicyFactory {
    ClimateProfile climateProfile();

    ComfortBand comfortBand();

    /** The thermal algorithm of the family, without compressor protection. */
    ThermalPolicy createThermalStrategy();

    VentilationPolicy createVentilationPolicy();

    LightingPolicy createLightingPolicy();

    default CompressorGuard compressorGuard() {
        return CompressorGuard.standard();
    }

    /** The thermal algorithm wrapped by the compressor-protection decorator. */
    default ThermalPolicy createThermalPolicy() {
        return new MinimumCycleThermalPolicy(createThermalStrategy(), compressorGuard());
    }
}
