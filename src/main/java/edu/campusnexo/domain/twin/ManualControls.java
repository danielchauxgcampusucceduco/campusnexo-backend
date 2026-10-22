package edu.campusnexo.domain.twin;

import java.time.Instant;

/**
 * Controls set by people: the teacher's manual climate control, the pop-up study room and maintenance.
 * Time-limited controls expire on their own.
 */
public record ManualControls(Instant overrideUntil, Instant popUpUntil, boolean maintenance) {
    public static ManualControls none() {
        return new ManualControls(null, null, false);
    }

    public boolean overrideActive(Instant now) {
        return overrideUntil != null && now.isBefore(overrideUntil);
    }

    public boolean popUpActive(Instant now) {
        return popUpUntil != null && now.isBefore(popUpUntil);
    }

    public ManualControls withOverrideUntil(Instant until) {
        return new ManualControls(until, popUpUntil, maintenance);
    }

    public ManualControls withPopUpUntil(Instant until) {
        return new ManualControls(overrideUntil, until, maintenance);
    }

    public ManualControls withMaintenance(boolean enabled) {
        return new ManualControls(overrideUntil, popUpUntil, enabled);
    }
}
