package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

/** HVAC recommendation. The setpoint keeps its decimals so the stored value is the one applied. */
public record HvacDecision(HvacMode mode, double setpointC, String explanation) {
    public HvacDecision {
        requireNonNull(mode, "hvac.mode");
        requireRange(setpointC, 16, 30, "hvac.setpointC");
        requireText(explanation, "hvac.explanation");
    }

    public boolean enabled() {
        return mode.isActive();
    }
}
