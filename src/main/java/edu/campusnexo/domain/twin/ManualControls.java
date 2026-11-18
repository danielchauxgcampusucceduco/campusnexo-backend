package edu.campusnexo.domain.twin;

import java.time.Instant;

/** Controls set by the staff: the pop-up study room and maintenance. The pop-up expires on its own. */
public record ManualControls(Instant popUpUntil, boolean maintenance) {
    public static ManualControls none() {
        return new ManualControls(null, false);
    }

    public boolean popUpActive(Instant now) {
        return popUpUntil != null && now.isBefore(popUpUntil);
    }

    public ManualControls withPopUpUntil(Instant until) {
        return new ManualControls(until, maintenance);
    }

    public ManualControls withMaintenance(boolean enabled) {
        return new ManualControls(popUpUntil, enabled);
    }
}
