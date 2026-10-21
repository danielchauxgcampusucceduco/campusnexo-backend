package edu.campusnexo.domain.twin;

import edu.campusnexo.domain.shared.DomainChecks;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An empty room with lights or computers on for longer than the idle threshold gets one shutdown order
 * for the Arduino. The order is not repeated within the same threshold, and a new empty period (people
 * came and left) allows a new one.
 */
public final class IdleEquipmentShutdownPolicy {
    private final Duration idleShutdown;

    public IdleEquipmentShutdownPolicy(Duration idleShutdown) {
        DomainChecks.require(idleShutdown != null && !idleShutdown.isNegative() && !idleShutdown.isZero(),
                "idleShutdown debe ser positivo");
        this.idleShutdown = idleShutdown;
    }

    public Optional<EquipmentCommand> decide(String spaceId, RoomCondition condition, RoomEquipment equipment,
                                             Instant emptySince, Instant lastShutdownAt, Instant now) {
        if (!condition.wastesEnergy() || emptySince == null
                || Duration.between(emptySince, now).compareTo(idleShutdown) < 0) {
            return Optional.empty();
        }
        boolean alreadyOrdered = lastShutdownAt != null && !lastShutdownAt.isBefore(emptySince)
                && Duration.between(lastShutdownAt, now).compareTo(idleShutdown) < 0;
        if (alreadyOrdered) {
            return Optional.empty();
        }
        long idleMinutes = Duration.between(emptySince, now).toMinutes();
        return Optional.of(new EquipmentCommand(UUID.randomUUID(), spaceId, equipment.lightsOn(),
                equipment.devicesOn() > 0, now, "Salón vacío hace " + idleMinutes + " min con " + describe(equipment)));
    }

    public static String describe(RoomEquipment equipment) {
        return "luces " + (equipment.lightsOn() ? "encendidas" : "apagadas") + ", " + equipment.devicesOn() + "/"
                + equipment.devicesTotal() + " equipos encendidos";
    }
}
