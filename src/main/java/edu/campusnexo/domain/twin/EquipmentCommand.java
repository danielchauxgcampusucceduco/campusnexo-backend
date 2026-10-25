package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.Instant;
import java.util.UUID;

/**
 * Order for the room's Arduino relays: switch off the lighting circuit and/or put the computers to sleep.
 * Emitted once when a room has been empty with equipment on for longer than the idle threshold.
 */
public record EquipmentCommand(UUID commandId, String spaceId, boolean lightsOff, boolean suspendDevices,
                               Instant issuedAt, String reason) {
    public EquipmentCommand {
        requireNonNull(commandId, "commandId");
        requireText(spaceId, "spaceId");
        requireNonNull(issuedAt, "issuedAt");
        requireText(reason, "reason");
    }
}
