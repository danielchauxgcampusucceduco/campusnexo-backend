package edu.campusnexo.domain.twin.hvac;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.twin.HvacControlState;

/** The HVAC setting decided by the chain, with the rule that decided it and why. */
public record HvacResolution(HvacControlState state, HvacMode mode, double setpointC, String explanation,
                             String decidedBy) {
    public HvacResolution {
        requireNonNull(state, "state");
        requireNonNull(mode, "mode");
        require(state.compressorOn() == mode.isActive(), "El modo " + mode + " no corresponde a la estrategia " + state);
        requireText(explanation, "explanation");
        requireText(decidedBy, "decidedBy");
    }

    public boolean compressorOn() {
        return state.compressorOn();
    }
}
