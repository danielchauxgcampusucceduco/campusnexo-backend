package edu.campusnexo.application.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.ForbiddenOperationException;
import edu.campusnexo.application.exception.ReservationConflictException;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.Requester;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CheckIn;
import edu.campusnexo.domain.persona.Docente;
import edu.campusnexo.domain.persona.Estudiante;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.InMemoryPersonaRepository;
import edu.campusnexo.support.InMemoryReservationRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final InMemoryPersonaRepository personas = new InMemoryPersonaRepository();
    private final ReservationService service = new ReservationService(clock, new InMemoryReservationRepository(),
            new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom(), TestData.library())),
            personas);
    private final Instant start = TestData.NOW.plus(Duration.ofHours(1));

    private CreateReservationCommand command(Instant from, Duration duration, int attendees) {
        return new CreateReservationCommand("AULA-301", from, from.plus(duration), attendees, "IS-701");
    }

    @Test
    @DisplayName("Rechaza reservas que se solapan con otra del mismo espacio")
    void rejectsOverlaps() {
        service.create(command(start, Duration.ofHours(2), 25));
        assertThrows(ReservationConflictException.class,
                () -> service.create(command(start.plus(Duration.ofHours(1)), Duration.ofHours(2), 10)));
        service.create(command(start.plus(Duration.ofHours(2)), Duration.ofHours(1), 10));
        assertEquals(2, service.list("AULA-301", null, null).size());
    }

    @Test
    @DisplayName("Rechaza reservas que superan el aforo")
    void rejectsOverCapacity() {
        assertThrows(DomainValidationException.class, () -> service.create(command(start, Duration.ofHours(1), 40)));
    }

    @Test
    @DisplayName("Si otra petición guardó primero, el rechazo del repositorio también se responde como conflicto")
    void storeRejectionIsAConflict() {
        InMemoryReservationRepository rows = new InMemoryReservationRepository() {
            @Override
            public java.util.List<edu.campusnexo.domain.reservation.Reservation> findOverlapping(String spaceId,
                    Instant from, Instant to) {
                return java.util.List.of(); // both requests read "no overlap" before either one saves
            }
        };
        ReservationService racing = new ReservationService(new MutableClock(TestData.NOW), rows,
                new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom())), personas);
        racing.create(command(start, Duration.ofHours(2), 25));
        assertThrows(ReservationConflictException.class, () -> racing.create(command(start, Duration.ofHours(2), 25)));
    }

    private CreateReservationCommand by(String author) {
        return new CreateReservationCommand("AULA-301", start, start.plus(Duration.ofHours(2)), 20, "IS-701",
                null, Requester.user(author));
    }

    private CreateReservationCommand heldBy(String spaceId, String holder, Duration duration) {
        return new CreateReservationCommand(spaceId, start, start.plus(duration), 10, "IS-701", holder,
                Requester.user("ana"));
    }

    @Test
    @DisplayName("Guarda quién creó la reserva como campo, no en el texto de la referencia")
    void recordsAuthor() {
        Reservation reservation = service.create(by("ana"));
        assertEquals("ana", reservation.createdBy());
    }

    @Test
    @DisplayName("El titular decide qué puede reservar: un docente reserva el aula, un estudiante no")
    void holderRulesArePolymorphic() {
        personas.save(new Docente("D-100", "Marta Ruiz", "Ingeniería de Software"));
        personas.save(new Estudiante("E-200", "Juan Paz", "Ingeniería de Sistemas"));

        assertEquals("D-100", service.create(heldBy("AULA-301", "D-100", Duration.ofHours(2))).holderCode());
        assertThrows(DomainValidationException.class,
                () -> service.create(heldBy("AULA-301", "E-200", Duration.ofHours(1))));
    }

    @Test
    @DisplayName("Cada titular tiene su duración máxima y debe estar registrado")
    void holderDurationAndRegistration() {
        personas.save(new Estudiante("E-200", "Juan Paz", "Ingeniería de Sistemas"));
        assertThrows(DomainValidationException.class,
                () -> service.create(heldBy("BIB-PISO-2", "E-200", Duration.ofHours(3))));
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(heldBy("AULA-301", "D-999", Duration.ofHours(1))));
    }

    @Test
    @DisplayName("El titular confirma su llegada con su código desde 15 minutos antes; repetirlo conserva la primera hora")
    void checkInByTheHolder() {
        personas.save(new Docente("D-100", "Marta Ruiz", "Ingeniería de Software"));
        Reservation booking = service.create(heldBy("AULA-301", "D-100", Duration.ofHours(2)));

        clock.advance(Duration.ofMinutes(50));
        assertThrows(DomainValidationException.class, () -> service.checkIn("AULA-301", "E-200"));
        CheckIn first = service.checkIn("AULA-301", "D-100");
        assertEquals(booking.reservationId(), first.reservation().reservationId());
        assertEquals("Marta Ruiz", first.persona().nombre());

        clock.advance(Duration.ofMinutes(20));
        assertEquals(first.reservation().checkedInAt(), service.checkIn("AULA-301", "D-100").reservation().checkedInAt());
        assertTrue(service.get(booking.reservationId()).checkedIn());
    }

    @Test
    @DisplayName("Sin reserva en curso ni por empezar no hay check-in")
    void checkInNeedsABooking() {
        assertThrows(ResourceNotFoundException.class, () -> service.checkIn("AULA-301", "D-100"));
    }

    @Test
    @DisplayName("Solo quien creó la reserva o un administrador puede cancelarla")
    void onlyTheAuthorOrAnAdministratorCancels() {
        Reservation reservation = service.create(by("ana"));

        ForbiddenOperationException denied = assertThrows(ForbiddenOperationException.class,
                () -> service.cancel(reservation.reservationId(), Requester.user("luis")));
        assertEquals("reservation_not_owned", denied.code());

        service.cancel(reservation.reservationId(), Requester.user("ana"));
        Reservation another = service.create(by("ana"));
        service.cancel(another.reservationId(), Requester.administrator("admin"));
        assertTrue(service.list("AULA-301", null, null).isEmpty());
    }

    @Test
    @DisplayName("Una reserva sin autor registrado solo la cancela un administrador")
    void legacyReservationsNeedAnAdministrator() {
        Reservation legacy = service.create(command(start, Duration.ofHours(1), 10));
        assertThrows(ForbiddenOperationException.class, () -> service.cancel(legacy.reservationId(), Requester.user("ana")));
        service.cancel(legacy.reservationId(), Requester.administrator("admin"));
    }
}
