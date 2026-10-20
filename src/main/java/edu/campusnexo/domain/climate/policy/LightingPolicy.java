package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.LightingDecision;

/** Strategy: algorithm that decides artificial light level and blinds. */
public interface LightingPolicy {
    LightingDecision recommend(ClimateContext context);
}
