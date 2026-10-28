package edu.campusnexo.domain.twin;

import java.time.Instant;
import java.util.UUID;

/** Since when the room has been empty and when the last shutdown order was sent (both may be unknown). */
/** {@code attendedReservation}: the active reservation once someone was seen or checked in (so it is no no-show). */
public record RoomTimers(Instant emptySince, Instant equipmentShutdownAt, UUID attendedReservation) {
    public RoomTimers(Instant emptySince, Instant equipmentShutdownAt) {
        this(emptySince, equipmentShutdownAt, null);
    }

    public static RoomTimers emptySince(Instant since) {
        return new RoomTimers(since, null);
    }
}
