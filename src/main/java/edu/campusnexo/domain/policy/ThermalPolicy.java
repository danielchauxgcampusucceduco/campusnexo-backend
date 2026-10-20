package edu.campusnexo.domain.policy;

/** Strategy for recommending HVAC operation for a campus climate profile. */
public interface ThermalPolicy { HvacDecision recommend(ClimateContext context); }
