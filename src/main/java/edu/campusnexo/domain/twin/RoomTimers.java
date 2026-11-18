package edu.campusnexo.domain.twin;

import java.time.Instant;
import java.util.UUID;

/**
 * Since when the room has been empty (unknown until a frame says so), and {@code attendedReservation}: the active
 * reservation once someone was seen or checked in (so it is no no-show).
 */
public record RoomTimers(Instant emptySince, UUID attendedReservation) {
    public static RoomTimers emptySince(Instant since) {
        return new RoomTimers(since, null);
    }
}
