package edu.campusnexo.application.port.in;

import java.time.Instant;

/**
 * A booking request; {@code requestedBy} becomes the author and {@code holderCode} names the person responsible
 * for the activity (a registered teacher or student), or {@code null} when nobody is named.
 */
public record CreateReservationCommand(
        String spaceId,
        Instant startsAt,
        Instant endsAt,
        int expectedAttendees,
        String reference,
        String holderCode,
        Requester requestedBy) {

    /** A booking by an unidentified person without holder (tests and tools). */
    public CreateReservationCommand(String spaceId, Instant startsAt, Instant endsAt, int expectedAttendees,
                                    String reference) {
        this(spaceId, startsAt, endsAt, expectedAttendees, reference, null, null);
    }
}
