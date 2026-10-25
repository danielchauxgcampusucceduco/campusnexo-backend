package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID alertId,
        String spaceId,
        AlertType type,
        AlertSeverity severity,
        Instant detectedAt,
        String message,
        double measuredValue,
        double thresholdValue) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(alert.alertId(), alert.spaceId(), alert.type(), alert.severity(), alert.detectedAt(),
                alert.message(), alert.measuredValue(), alert.thresholdValue());
    }
}
