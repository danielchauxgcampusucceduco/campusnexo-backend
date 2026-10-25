package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacDecision;

/** Strategy: algorithm that decides the HVAC mode for a space. */
public interface ThermalPolicy {
    HvacDecision recommend(ClimateContext context);
}
