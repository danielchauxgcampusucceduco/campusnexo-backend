package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.climate.HvacMode;
import java.time.Duration;
import java.time.Instant;

/**
 * What the room's climate unit is doing and since when: the strategy, the mode (heat, cool, dehumidify, off)
 * and the setpoint, with the reason given by the HVAC rule that decided it. This is the state actually
 * commanded to the ESP32, after every HVAC rule.
 */
public record HvacSetting(HvacControlState state, HvacMode mode, double setpointC, Instant since, String reason) {
    public HvacSetting {
        requireNonNull(state, "hvac.state");
        requireNonNull(mode, "hvac.mode");
        requireNonNull(since, "hvac.since");
        require(Double.isFinite(setpointC), "hvac.setpointC no válido");
        requireText(reason, "hvac.reason");
        require(state.compressorOn() == mode.isActive(), "El modo " + mode + " no corresponde a la estrategia " + state);
    }

    public static HvacSetting off(Instant since) {
        return new HvacSetting(HvacControlState.APAGADO, HvacMode.APAGADO, 21.0, since, "Sin lecturas todavía");
    }

    public boolean compressorOn() {
        return state.compressorOn();
    }

    public long minutesSince(Instant now) {
        return Math.max(0, Duration.between(since, now).toMinutes());
    }

    /** The next setting; it keeps the start time when the strategy and the mode stay the same. */
    public HvacSetting moveTo(HvacControlState nextState, HvacMode nextMode, double nextSetpointC, String nextReason,
                              Instant now) {
        boolean unchanged = nextState == state && nextMode == mode;
        return new HvacSetting(nextState, nextMode, nextSetpointC, unchanged ? since : now, nextReason);
    }

    /** Whether the unit must receive a new command to go from {@code other} to this setting. */
    public boolean differsFrom(HvacSetting other) {
        return state != other.state || mode != other.mode
                || (compressorOn() && Double.compare(setpointC, other.setpointC) != 0);
    }
}
