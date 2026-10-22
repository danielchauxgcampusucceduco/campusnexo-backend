package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.reservation.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationUseCase {
    Reservation create(CreateReservationCommand command);

    Reservation get(UUID reservationId);

    /** {@code from} and {@code to} are optional; default window is one day back and seven ahead. */
    List<Reservation> list(String spaceId, Instant from, Instant to);

    Optional<Reservation> activeAt(String spaceId, Instant instant);

    /**
     * Cancels a booking. An administrator may cancel any booking; anyone else only the bookings they made.
     *
     * @throws edu.campusnexo.application.exception.ForbiddenOperationException otherwise
     */
    void cancel(UUID reservationId, Requester requester);
}
