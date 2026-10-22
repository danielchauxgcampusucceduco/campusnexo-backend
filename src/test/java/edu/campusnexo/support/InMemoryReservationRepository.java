package edu.campusnexo.support;

import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.reservation.ReservationOverlapException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryReservationRepository implements ReservationRepository {
    private final List<Reservation> rows = new ArrayList<>();

    @Override
    public synchronized Reservation save(Reservation reservation) {
        if (!overlapping(reservation.spaceId(), reservation.startsAt(), reservation.endsAt()).isEmpty()) {
            throw new ReservationOverlapException(reservation.spaceId());
        }
        rows.add(reservation);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(UUID reservationId) {
        return rows.stream().filter(row -> row.reservationId().equals(reservationId)).findFirst();
    }

    @Override
    public synchronized List<Reservation> findOverlapping(String spaceId, Instant from, Instant to) {
        return overlapping(spaceId, from, to);
    }

    /** What the database exclusion constraint checks, independent of what callers query first. */
    private List<Reservation> overlapping(String spaceId, Instant from, Instant to) {
        return rows.stream().filter(row -> row.spaceId().equals(spaceId))
                .filter(row -> row.startsAt().isBefore(to) && row.endsAt().isAfter(from))
                .sorted(Comparator.comparing(Reservation::startsAt)).toList();
    }

    @Override
    public Optional<Reservation> findActiveAt(String spaceId, Instant instant) {
        return rows.stream().filter(row -> row.spaceId().equals(spaceId) && row.isActiveAt(instant)).findFirst();
    }

    @Override
    public Map<String, Reservation> findActiveAtForAll(Instant instant) {
        Map<String, Reservation> active = new LinkedHashMap<>();
        rows.stream().filter(row -> row.isActiveAt(instant)).forEach(row -> active.putIfAbsent(row.spaceId(), row));
        return active;
    }

    @Override
    public Optional<Reservation> findNextStartingAfter(String spaceId, Instant instant) {
        return rows.stream().filter(row -> row.spaceId().equals(spaceId) && row.startsAt().isAfter(instant))
                .min(Comparator.comparing(Reservation::startsAt));
    }

    @Override
    public void delete(UUID reservationId) {
        rows.removeIf(row -> row.reservationId().equals(reservationId));
    }
}
