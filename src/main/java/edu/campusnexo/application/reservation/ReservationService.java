package edu.campusnexo.application.reservation;

import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.exception.ForbiddenOperationException;
import edu.campusnexo.application.exception.ReservationConflictException;
import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.Requester;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.CheckIn;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.port.PersonaRepository;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.reservation.ReservationOverlapException;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps bookings consistent with capacity and free of overlaps. The overlap query gives a fast, friendly
 * answer; the guarantee is the repository's, which rejects atomically a reservation that overlaps another
 * one stored by a concurrent request (both answer {@code 409 reservation_conflict}). A booking that names its
 * holder follows that person's own rules (what they may book and for how long), and the holder later confirms
 * attendance at the room's kiosk.
 */
public class ReservationService implements ReservationUseCase {
    static final Duration MAX_QUERY_WINDOW = Duration.ofDays(31);

    private final Clock clock;
    private final ReservationRepository repository;
    private final CampusCatalogUseCase catalog;
    private final PersonaRepository personas;
    private final SpaceRepositoryPort twins;

    public ReservationService(Clock clock, ReservationRepository repository, CampusCatalogUseCase catalog,
                              PersonaRepository personas, SpaceRepositoryPort twins) {
        this.clock = clock;
        this.repository = repository;
        this.catalog = catalog;
        this.personas = personas;
        this.twins = twins;
    }

    @Override
    public Reservation create(CreateReservationCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        Reservation reservation = new Reservation(UUID.randomUUID(), space.spaceId(), command.startsAt(),
                command.endsAt(), command.expectedAttendees(), command.reference(),
                command.requestedBy() == null ? null : command.requestedBy().name(), command.holderCode(), null);
        if (reservation.holderCode() != null) {
            checkHolderMayBook(holder(reservation.holderCode()), space, reservation);
        }
        if (reservation.expectedAttendees() > space.capacity()) {
            throw new DomainValidationException("expectedAttendees supera el aforo del espacio (" + space.capacity() + ")");
        }
        if (reservation.endsAt().isBefore(clock.instant())) {
            throw new DomainValidationException("No se pueden crear reservas que ya terminaron");
        }
        if (twins.findTwin(space.spaceId()).map(SpaceTwinState::maintenance).orElse(false)) {
            throw new ConflictException("space_under_maintenance", space.spaceId()
                    + " está en mantenimiento; termina el mantenimiento antes de reservarlo");
        }
        if (!repository.findOverlapping(space.spaceId(), reservation.startsAt(), reservation.endsAt()).isEmpty()) {
            throw new ReservationConflictException(space.spaceId());
        }
        try {
            return repository.save(reservation);
        } catch (ReservationOverlapException concurrent) {
            throw new ReservationConflictException(space.spaceId());
        }
    }

    @Override
    public CheckIn checkIn(String spaceId, String codigo) {
        catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        Reservation booking = repository.findActiveAt(spaceId, now)
                .or(() -> repository.findNextStartingAfter(spaceId, now)
                        .filter(next -> !next.startsAt().isAfter(now.plus(Reservation.CHECK_IN_EARLY))))
                .orElseThrow(() -> new ResourceNotFoundException("reservation_not_found",
                        "No hay una reserva en curso o por empezar en " + spaceId));
        Reservation checkedIn = booking.checkIn(codigo, now);
        if (!booking.checkedIn()) {
            repository.recordCheckIn(checkedIn.reservationId(), checkedIn.checkedInAt());
        }
        return new CheckIn(checkedIn, holder(codigo));
    }

    private Persona holder(String codigo) {
        return personas.findByCodigo(codigo).orElseThrow(() -> new ResourceNotFoundException("persona_not_found",
                "No hay una persona registrada con el código " + codigo));
    }

    /** Each kind of person answers what it may book and for how long; the service does not switch on the kind. */
    private static void checkHolderMayBook(Persona persona, CampusSpace space, Reservation reservation) {
        if (!persona.puedeReservar(space.type())) {
            throw new DomainValidationException("Un " + persona.tipo() + " no puede reservar espacios de tipo "
                    + space.type());
        }
        if (Duration.between(reservation.startsAt(), reservation.endsAt()).compareTo(persona.reservaMaxima()) > 0) {
            throw new DomainValidationException("Un " + persona.tipo() + " puede reservar máximo "
                    + persona.reservaMaxima().toHours() + " horas");
        }
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
    public void cancel(UUID reservationId, Requester requester) {
        Reservation reservation = get(reservationId);
        boolean owner = reservation.createdBy() != null && reservation.createdBy().equals(requester.name());
        if (!requester.administrator() && !owner) {
            throw new ForbiddenOperationException("reservation_not_owned",
                    "Solo quien creó la reserva o un administrador puede cancelarla");
        }
        repository.delete(reservationId);
    }
}
