package edu.campusnexo.domain.alert;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.Instant;
import java.util.UUID;

/** Alert raised by a telemetry observer. Values are kept so the alert can be audited later. */
public record Alert(
        UUID alertId,
        String spaceId,
        AlertType type,
        AlertSeverity severity,
        Instant detectedAt,
        String message,
        double measuredValue,
        double thresholdValue) {

    public Alert {
        requireNonNull(alertId, "alertId");
        requireText(spaceId, "spaceId");
        requireNonNull(type, "type");
        requireNonNull(severity, "severity");
        requireNonNull(detectedAt, "detectedAt");
        requireText(message, "message");
    }

    public static Alert raise(String spaceId, AlertType type, AlertSeverity severity, Instant detectedAt,
                              String message, double measuredValue, double thresholdValue) {
        return new Alert(UUID.randomUUID(), spaceId, type, severity, detectedAt, message, measuredValue, thresholdValue);
    }
}
