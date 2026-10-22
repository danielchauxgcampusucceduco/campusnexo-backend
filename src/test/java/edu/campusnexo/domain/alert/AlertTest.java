package edu.campusnexo.domain.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertTest {

    @Test
    void raiseCreatesAnAuditableAlertWithMeasuredAndThresholdValues() {
        Instant at = Instant.parse("2026-10-21T15:00:00Z");

        Alert alert = Alert.raise("AULA-301", AlertType.CO2_ALTO, AlertSeverity.ADVERTENCIA,
                at, "Ventilar", 1_200, 1_000);

        assertNotNull(alert.alertId());
        assertEquals(1_200, alert.measuredValue());
        assertEquals(1_000, alert.thresholdValue());
        assertEquals(at, alert.detectedAt());
    }

    @Test
    void rejectsAnAlertWithoutItsSpaceOrTimestamp() {
        assertThrows(DomainValidationException.class, () -> Alert.raise(
                " ", AlertType.CO2_ALTO, AlertSeverity.CRITICA, Instant.now(), "Ventilar", 1_200, 1_000));
        assertThrows(DomainValidationException.class, () -> Alert.raise(
                "AULA-301", AlertType.CO2_ALTO, AlertSeverity.CRITICA, null, "Ventilar", 1_200, 1_000));
    }
}
