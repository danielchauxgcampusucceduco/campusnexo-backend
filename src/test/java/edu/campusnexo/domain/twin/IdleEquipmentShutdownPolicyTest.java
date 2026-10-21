package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdleEquipmentShutdownPolicyTest {
    private static final Instant EMPTY_SINCE = Instant.parse("2026-10-20T15:00:00Z");
    private final IdleEquipmentShutdownPolicy policy = new IdleEquipmentShutdownPolicy(Duration.ofMinutes(5));
    private final RoomEquipment wasting = new RoomEquipment(true, 400, 3, 24);

    @Test
    @DisplayName("Antes del umbral no hay orden; al cumplirlo, una orden con luces y equipos")
    void ordersOnlyAfterThreshold() {
        assertFalse(policy.decide("309-A", RoomCondition.LUCES_Y_EQUIPOS_SIN_USO, wasting, EMPTY_SINCE, null,
                EMPTY_SINCE.plus(Duration.ofMinutes(4))).isPresent());
        EquipmentCommand command = policy.decide("309-A", RoomCondition.LUCES_Y_EQUIPOS_SIN_USO, wasting, EMPTY_SINCE,
                null, EMPTY_SINCE.plus(Duration.ofMinutes(5))).orElseThrow();
        assertTrue(command.lightsOff());
        assertTrue(command.suspendDevices());
    }

    @Test
    @DisplayName("No repite la orden dentro del mismo umbral, pero sí en un nuevo periodo vacío")
    void doesNotRepeatWithinThreshold() {
        Instant ordered = EMPTY_SINCE.plus(Duration.ofMinutes(5));
        assertFalse(policy.decide("309-A", RoomCondition.LUCES_SIN_OCUPACION, wasting, EMPTY_SINCE, ordered,
                ordered.plus(Duration.ofMinutes(2))).isPresent());
        Instant newEmptyPeriod = ordered.plus(Duration.ofMinutes(30));
        assertTrue(policy.decide("309-A", RoomCondition.LUCES_SIN_OCUPACION, wasting, newEmptyPeriod, ordered,
                newEmptyPeriod.plus(Duration.ofMinutes(6))).isPresent());
    }

    @Test
    @DisplayName("Un salón ocupado o sin energía encendida nunca recibe orden")
    void noOrderWithoutWaste() {
        assertFalse(policy.decide("309-A", RoomCondition.OCUPADO, wasting, null, null, EMPTY_SINCE).isPresent());
        assertFalse(policy.decide("309-A", RoomCondition.LIBRE, wasting, EMPTY_SINCE, null,
                EMPTY_SINCE.plus(Duration.ofHours(1))).isPresent());
    }
}
