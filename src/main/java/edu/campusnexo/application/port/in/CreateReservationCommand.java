package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.reservation.ReservationOrigin;
import java.time.Instant;

/** A booking request; {@code requestedBy} becomes the author, and the demonstration uses {@code SIMULACION}. */
public record CreateReservationCommand(
        String spaceId,
        Instant startsAt,
        Instant endsAt,
        int expectedAttendees,
        String reference,
        ReservationOrigin origin,
        Requester requestedBy) {

    /** A booking by an unidentified person (tests and tools). */
    public CreateReservationCommand(String spaceId, Instant startsAt, Instant endsAt, int expectedAttendees,
                                    String reference) {
        this(spaceId, startsAt, endsAt, expectedAttendees, reference, ReservationOrigin.USUARIO, null);
    }
}
