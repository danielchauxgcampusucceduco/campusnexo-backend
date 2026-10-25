package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.domain.reservation.Reservation;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        String spaceId,
        Instant startsAt,
        Instant endsAt,
        int expectedAttendees,
        String reference,
        String createdBy,
        String holderCode,
        Instant checkedInAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.reservationId(), reservation.spaceId(), reservation.startsAt(),
                reservation.endsAt(), reservation.expectedAttendees(), reservation.reference(), reservation.createdBy(),
                reservation.holderCode(), reservation.checkedInAt());
    }
}
