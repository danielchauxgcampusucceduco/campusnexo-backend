package edu.campusnexo.application.twin;

import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import java.time.Instant;

/**
 * The reservations that matter for deciding a room now: the one in progress and the next one after it.
 * They are read once, before the twin is locked, so the decision itself touches no repository and can be
 * recomputed after a concurrent write without querying again.
 */
record ReservationContext(Reservation active, Reservation next) {

    static ReservationContext lookup(ReservationRepository reservations, String spaceId, Instant now) {
        Reservation active = reservations.findActiveAt(spaceId, now).orElse(null);
        Instant after = active == null ? now : active.endsAt();
        return new ReservationContext(active, reservations.findNextStartingAfter(spaceId, after).orElse(null));
    }

    static ReservationContext none() {
        return new ReservationContext(null, null);
    }
}
