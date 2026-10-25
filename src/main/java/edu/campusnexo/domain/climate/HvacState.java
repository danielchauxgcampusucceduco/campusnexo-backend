package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;

/** Current HVAC mode and how long it has been in that mode. */
public record HvacState(HvacMode mode, int minutesInMode) {
    public static final int MAX_MINUTES = 525_600;

    public HvacState {
        requireNonNull(mode, "currentHvacMode");
        requireRange(minutesInMode, 0, MAX_MINUTES, "minutesInCurrentHvacMode");
    }

    /** State assumed when no previous recommendation exists: off for a long time. */
    public static HvacState initial() {
        return new HvacState(HvacMode.APAGADO, 24 * 60);
    }
}
