package edu.campusnexo.application.port.in;

import java.time.Instant;

public record CreateReservationCommand(
        String spaceId,
        Instant startsAt,
        Instant endsAt,
        int expectedAttendees,
        String reference) {
}
