package edu.campusnexo.domain.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationCheckInTest {
    private static final Instant START = TestData.NOW.plus(Duration.ofHours(1));
    private final Reservation booking = new Reservation(UUID.randomUUID(), "301-A", START, START.plus(Duration.ofHours(2)),
            20, "IS-701", "ana", "D-100", null);

    @Test
    @DisplayName("El titular confirma su llegada desde 15 minutos antes y hasta el final")
    void holderChecksInWithinTheWindow() {
        Reservation checkedIn = booking.checkIn("D-100", START.minus(Duration.ofMinutes(10)));
        assertTrue(checkedIn.checkedIn());
        assertEquals(START.minus(Duration.ofMinutes(10)), checkedIn.checkedInAt());
        assertSame(checkedIn, checkedIn.checkIn("D-100", START.plus(Duration.ofMinutes(5))));
        assertFalse(booking.checkedIn());
    }

    @Test
    @DisplayName("Rechaza otro código, fuera de la ventana o una reserva sin titular")
    void rejectsWrongCodeOrTime() {
        assertThrows(DomainValidationException.class, () -> booking.checkIn("E-200", START));
        assertThrows(DomainValidationException.class, () -> booking.checkIn("D-100", START.minus(Duration.ofMinutes(30))));
        assertThrows(DomainValidationException.class, () -> booking.checkIn("D-100", START.plus(Duration.ofHours(2))));
        Reservation anonymous = new Reservation(UUID.randomUUID(), "301-A", START, START.plusSeconds(3600), 5, "IS-1");
        assertThrows(DomainValidationException.class, () -> anonymous.checkIn("D-100", START));
    }
}
