package edu.campusnexo.domain.telemetry;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Duration;
import java.time.Instant;

/**
 * Which capture times are believable: not further in the future than the allowed clock skew of a device,
 * and not older than the retention window. Checked before any state changes, so a rejected reading leaves
 * no trace in the twin or in the history.
 */
public record ReadingAdmission(Duration maxFutureSkew, Duration maxAge) {
    public ReadingAdmission {
        if (maxFutureSkew == null || maxFutureSkew.isNegative() || maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Ventanas de admisión de lecturas no válidas");
        }
    }

    public void check(Instant capturedAt, Instant now) {
        if (capturedAt.isAfter(now.plus(maxFutureSkew))) {
            throw new DomainValidationException("recordedAt no puede estar en el futuro");
        }
        if (capturedAt.isBefore(now.minus(maxAge))) {
            throw new DomainValidationException("recordedAt es más antiguo que " + maxAge.toDays() + " días");
        }
    }
}
