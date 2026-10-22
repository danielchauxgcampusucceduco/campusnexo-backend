package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.climate.HvacMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Command for the room controller (ESP32), emitted only when the setting changes: the strategy, what the
 * unit does in it (heat, cool, dehumidify, off) and the setpoint.
 */
public record HvacCommand(UUID commandId, String spaceId, HvacControlState state, HvacMode mode,
                          boolean compressorOn, double setpointC, Instant issuedAt, String reason) {
    public HvacCommand {
        requireNonNull(commandId, "commandId");
        requireText(spaceId, "spaceId");
        requireNonNull(state, "state");
        requireNonNull(mode, "mode");
        requireNonNull(issuedAt, "issuedAt");
        requireText(reason, "reason");
    }

    public static HvacCommand of(String spaceId, HvacSetting setting, String reason, Instant issuedAt) {
        return new HvacCommand(UUID.randomUUID(), spaceId, setting.state(), setting.mode(), setting.compressorOn(),
                setting.setpointC(), issuedAt, reason);
    }
}
