package edu.campusnexo.domain.reservation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationTest {
    private static final Instant START = Instant.parse("2026-10-20T14:00:00Z");

    @Test
    @DisplayName("Una reserva está activa en [inicio, fin)")
    void activeInterval() {
        Reservation reservation = new Reservation(UUID.randomUUID(), "AULA-301", START, START.plusSeconds(7200), 25, "IS-701");
        assertTrue(reservation.isActiveAt(START));
        assertTrue(reservation.isActiveAt(START.plusSeconds(3600)));
        assertFalse(reservation.isActiveAt(START.plusSeconds(7200)));
    }

    @Test
    @DisplayName("Rechaza intervalos invertidos y reservas de más de 12 horas")
    void rejectsInvalidIntervals() {
        assertThrows(DomainValidationException.class,
                () -> new Reservation(UUID.randomUUID(), "AULA-301", START, START, 25, "IS-701"));
        assertThrows(DomainValidationException.class,
                () -> new Reservation(UUID.randomUUID(), "AULA-301", START, START.plusSeconds(13 * 3600), 25, "IS-701"));
    }
}
