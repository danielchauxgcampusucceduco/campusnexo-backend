package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.Instant;
import java.util.UUID;

/** Command for the room controller (ESP32), emitted only when the HVAC strategy changes. */
public record HvacCommand(UUID commandId, String spaceId, HvacControlState state, boolean compressorOn,
                          double setpointC, Instant issuedAt, String reason) {
    public HvacCommand {
        requireNonNull(commandId, "commandId");
        requireText(spaceId, "spaceId");
        requireNonNull(state, "state");
        requireNonNull(issuedAt, "issuedAt");
        requireText(reason, "reason");
    }

    public static HvacCommand of(String spaceId, ThermalDecision decision, HvacControlState state, Instant issuedAt) {
        return new HvacCommand(UUID.randomUUID(), spaceId, state, state.compressorOn(), decision.setpointC(), issuedAt,
                decision.explanation());
    }
}
