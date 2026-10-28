package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.campusnexo.domain.reservation.Reservation;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpaceStatusPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private final SpaceStatusPolicy policy = new SpaceStatusPolicy(Duration.ofMinutes(15), 0.3);

    private static Reservation startedMinutesAgo(long minutes, int attendees) {
        Instant start = NOW.minus(Duration.ofMinutes(minutes));
        return new Reservation(UUID.randomUUID(), "AULA-301", start, start.plus(Duration.ofHours(2)), attendees, "IS-701");
    }

    @Test
    @DisplayName("Reserva sin asistentes: en espera durante el margen y luego liberada por no-show")
    void noShow() {
        assertEquals(SpaceStatus.RESERVADO_EN_ESPERA, policy.classify(false, false, 0, 32, startedMinutesAgo(5, 25), NOW));
        assertEquals(SpaceStatus.LIBERADO_POR_NO_SHOW, policy.classify(false, false, 0, 32, startedMinutesAgo(20, 25), NOW));
    }

    @Test
    @DisplayName("Asistencia normal, subutilización y sobrecupo")
    void occupancyLevels() {
        assertEquals(SpaceStatus.OCUPADO_OPTIMO, policy.classify(false, false, 22, 32, startedMinutesAgo(10, 25), NOW));
        assertEquals(SpaceStatus.ALERTA_SUBUTILIZADO, policy.classify(false, false, 6, 32, startedMinutesAgo(10, 30), NOW));
        assertEquals(SpaceStatus.ALERTA_SOBRECUPO, policy.classify(false, false, 40, 32, startedMinutesAgo(10, 30), NOW));
    }

    @Test
    @DisplayName("Mantenimiento y aula pop-up tienen precedencia")
    void manualControlsTakePrecedence() {
        assertEquals(SpaceStatus.MANTENIMIENTO, policy.classify(true, true, 40, 32, null, NOW));
        assertEquals(SpaceStatus.HABILITADO_POPUP_PARCIALES, policy.classify(false, true, 9, 32, null, NOW));
        assertEquals(SpaceStatus.LIBRE, policy.classify(false, false, 0, 32, null, NOW));
    }

    @Test
    void anAttendedReservationLeftEmptyIsNotANoShow() {
        assertEquals(SpaceStatus.LIBRE, policy.classify(false, false, 0, 32, startedMinutesAgo(40, 25), true, NOW));
        assertEquals(SpaceStatus.LIBERADO_POR_NO_SHOW,
                policy.classify(false, false, 0, 32, startedMinutesAgo(40, 25), false, NOW));
    }
}
