package edu.campusnexo.application.reservation;

import edu.campusnexo.application.exception.ReservationConflictException;
import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps bookings consistent with capacity and free of overlaps. The overlap check is done by the
 * application; concurrent requests could still collide (acceptable for the prototype).
 */
public class ReservationService implements ReservationUseCase {
    static final Duration MAX_QUERY_WINDOW = Duration.ofDays(31);

    private final Clock clock;
    private final ReservationRepository repository;
    private final CampusCatalogUseCase catalog;

    public ReservationService(Clock clock, ReservationRepository repository, CampusCatalogUseCase catalog) {
        this.clock = clock;
        this.repository = repository;
        this.catalog = catalog;
    }

    @Override
    public Reservation create(CreateReservationCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        Reservation reservation = new Reservation(UUID.randomUUID(), space.spaceId(), command.startsAt(),
                command.endsAt(), command.expectedAttendees(), command.reference());
        if (reservation.expectedAttendees() > space.capacity()) {
            throw new DomainValidationException("expectedAttendees supera el aforo del espacio (" + space.capacity() + ")");
        }
        if (reservation.endsAt().isBefore(clock.instant())) {
            throw new DomainValidationException("No se pueden crear reservas que ya terminaron");
        }
        if (!repository.findOverlapping(space.spaceId(), reservation.startsAt(), reservation.endsAt()).isEmpty()) {
            throw new ReservationConflictException(space.spaceId());
        }
        return repository.save(reservation);
    }

    @Override
    public Reservation get(UUID reservationId) {
        return repository.findById(reservationId).orElseThrow(() -> new ResourceNotFoundException(
                "reservation_not_found", "No existe la reserva: " + reservationId));
    }

    @Override
    public List<Reservation> list(String spaceId, Instant from, Instant to) {
        catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        Instant start = from == null ? now.minus(Duration.ofDays(1)) : from;
        Instant end = to == null ? now.plus(Duration.ofDays(7)) : to;
        if (!end.isAfter(start)) {
            throw new DomainValidationException("to debe ser posterior a from");
        }
        if (Duration.between(start, end).compareTo(MAX_QUERY_WINDOW) > 0) {
            throw new DomainValidationException("La consulta admite máximo " + MAX_QUERY_WINDOW.toDays() + " días");
        }
        return repository.findOverlapping(spaceId, start, end);
    }

    @Override
    public Optional<Reservation> activeAt(String spaceId, Instant instant) {
        return repository.findActiveAt(spaceId, instant);
    }

    @Override
    public void cancel(UUID reservationId) {
        get(reservationId);
        repository.delete(reservationId);
    }
}
