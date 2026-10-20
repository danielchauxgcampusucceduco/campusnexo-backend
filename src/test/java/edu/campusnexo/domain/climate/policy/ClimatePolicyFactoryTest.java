package edu.campusnexo.domain.climate.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import edu.campusnexo.domain.catalog.ClimateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClimatePolicyFactoryTest {

    @Test
    @DisplayName("Cada familia declara su perfil climático")
    void factoriesDeclareTheirProfile() {
        assertEquals(ClimateProfile.SEDE_ANDINA, new AndeanClimatePolicyFactory().climateProfile());
        assertEquals(ClimateProfile.SEDE_TROPICAL, new TropicalClimatePolicyFactory().climateProfile());
        assertEquals(ClimateProfile.SEDE_TEMPLADA, new MildClimatePolicyFactory().climateProfile());
    }

    @Test
    @DisplayName("Andina: calefacción primero, ventilación mecánica y aprovechamiento de luz natural")
    void andeanFamily() {
        ClimatePolicyFactory factory = new AndeanClimatePolicyFactory();
        MinimumCycleThermalPolicy thermal = assertInstanceOf(MinimumCycleThermalPolicy.class, factory.createThermalPolicy());
        assertInstanceOf(HeatingFirstThermalPolicy.class, thermal.delegate());
        assertInstanceOf(Co2ThresholdVentilationPolicy.class, factory.createVentilationPolicy());
        assertInstanceOf(DaylightHarvestingPolicy.class, factory.createLightingPolicy());
    }

    @Test
    @DisplayName("Tropical: enfriamiento con deshumidificación y control de deslumbramiento")
    void tropicalFamily() {
        ClimatePolicyFactory factory = new TropicalClimatePolicyFactory();
        MinimumCycleThermalPolicy thermal = assertInstanceOf(MinimumCycleThermalPolicy.class, factory.createThermalPolicy());
        assertInstanceOf(CoolingWithDehumidificationPolicy.class, thermal.delegate());
        assertInstanceOf(Co2ThresholdVentilationPolicy.class, factory.createVentilationPolicy());
        assertInstanceOf(GlareControlLightingPolicy.class, factory.createLightingPolicy());
    }

    @Test
    @DisplayName("Templada: banda de confort con ventilación natural")
    void mildFamily() {
        ClimatePolicyFactory factory = new MildClimatePolicyFactory();
        MinimumCycleThermalPolicy thermal = assertInstanceOf(MinimumCycleThermalPolicy.class, factory.createThermalPolicy());
        assertInstanceOf(ComfortBandThermalPolicy.class, thermal.delegate());
        assertInstanceOf(NaturalVentilationPolicy.class, factory.createVentilationPolicy());
        assertInstanceOf(DaylightHarvestingPolicy.class, factory.createLightingPolicy());
    }

    @Test
    @DisplayName("Las familias producen algoritmos térmicos distintos, no solo parámetros distintos")
    void familiesUseDifferentThermalAlgorithms() {
        Class<?> andean = ((MinimumCycleThermalPolicy) new AndeanClimatePolicyFactory().createThermalPolicy()).delegate().getClass();
        Class<?> tropical = ((MinimumCycleThermalPolicy) new TropicalClimatePolicyFactory().createThermalPolicy()).delegate().getClass();
        Class<?> mild = ((MinimumCycleThermalPolicy) new MildClimatePolicyFactory().createThermalPolicy()).delegate().getClass();
        assertNotEquals(andean, tropical);
        assertNotEquals(tropical, mild);
        assertNotEquals(andean, mild);
    }
}
