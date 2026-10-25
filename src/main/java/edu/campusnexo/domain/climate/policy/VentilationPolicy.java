package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.VentilationDecision;

/** Strategy: algorithm that decides how to renew the air of a space. */
public interface VentilationPolicy {
    VentilationDecision recommend(ClimateContext context);
}
