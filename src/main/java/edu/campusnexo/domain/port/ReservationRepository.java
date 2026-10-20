package edu.campusnexo.domain.port;

import edu.campusnexo.domain.reservation.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Reservation save(Reservation reservation);

    Optional<Reservation> findById(UUID reservationId);

    /** Reservations of the space that overlap the half-open interval [from, to). */
    List<Reservation> findOverlapping(String spaceId, Instant from, Instant to);

    Optional<Reservation> findActiveAt(String spaceId, Instant instant);

    /** First reservation of the space that starts strictly after {@code instant}. */
    Optional<Reservation> findNextStartingAfter(String spaceId, Instant instant);

    void delete(UUID reservationId);
}
