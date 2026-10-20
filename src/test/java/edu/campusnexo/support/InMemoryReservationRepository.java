package edu.campusnexo.support;

import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryReservationRepository implements ReservationRepository {
    private final List<Reservation> rows = new ArrayList<>();

    @Override
    public Reservation save(Reservation reservation) {
        rows.add(reservation);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(UUID reservationId) {
        return rows.stream().filter(row -> row.reservationId().equals(reservationId)).findFirst();
    }

    @Override
    public List<Reservation> findOverlapping(String spaceId, Instant from, Instant to) {
        return rows.stream().filter(row -> row.spaceId().equals(spaceId))
                .filter(row -> row.startsAt().isBefore(to) && row.endsAt().isAfter(from))
                .sorted(Comparator.comparing(Reservation::startsAt)).toList();
    }

    @Override
    public Optional<Reservation> findActiveAt(String spaceId, Instant instant) {
        return rows.stream().filter(row -> row.spaceId().equals(spaceId) && row.isActiveAt(instant)).findFirst();
    }
}
