package edu.campusnexo.infrastructure.persistence.alert;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
class AlertEntity {
    @Id
    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "space_id", nullable = false, length = 32)
    private String spaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AlertSeverity severity;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "measured_value", nullable = false)
    private double measuredValue;

    @Column(name = "threshold_value", nullable = false)
    private double thresholdValue;

    protected AlertEntity() {
    }

    AlertEntity(Alert alert) {
        alertId = alert.alertId();
        spaceId = alert.spaceId();
        type = alert.type();
        severity = alert.severity();
        detectedAt = alert.detectedAt();
        message = alert.message();
        measuredValue = alert.measuredValue();
        thresholdValue = alert.thresholdValue();
    }

    Alert toDomain() {
        return new Alert(alertId, spaceId, type, severity, detectedAt, message, measuredValue, thresholdValue);
    }
}
