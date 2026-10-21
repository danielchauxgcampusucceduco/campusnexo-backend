package edu.campusnexo.application.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.ReservationConflictException;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.InMemoryReservationRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {
    private final ReservationService service = new ReservationService(new MutableClock(TestData.NOW),
            new InMemoryReservationRepository(),
            new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom())));
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
                new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom())));
        racing.create(command(start, Duration.ofHours(2), 25));
        assertThrows(ReservationConflictException.class, () -> racing.create(command(start, Duration.ofHours(2), 25)));
    }
}
