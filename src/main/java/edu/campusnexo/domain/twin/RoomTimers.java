package edu.campusnexo.domain.twin;

import java.time.Instant;

/** Since when the room has been empty and when the last shutdown order was sent (both may be unknown). */
public record RoomTimers(Instant emptySince, Instant equipmentShutdownAt) {
    public static RoomTimers emptySince(Instant since) {
        return new RoomTimers(since, null);
    }
}
