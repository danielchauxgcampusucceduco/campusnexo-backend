package edu.campusnexo.application.alert;

import java.time.Duration;

/** Demo thresholds; they must be calibrated with the campus baseline before real use. */
public record AlertThresholds(
        Duration cooldown,
        Duration reservationGrace) {

    public AlertThresholds {
        if (cooldown.isNegative() || reservationGrace.isNegative()) {
            throw new IllegalArgumentException("Umbrales de alerta no válidos");
        }
    }
}
