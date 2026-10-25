package edu.campusnexo.application.alert;

import java.time.Duration;

/** Demo thresholds; they must be calibrated with the campus baseline before real use. */
public record AlertThresholds(
        double co2Ppm,
        double emptySpacePowerKw,
        Duration cooldown,
        Duration reservationGrace) {

    public AlertThresholds {
        if (co2Ppm <= 0 || emptySpacePowerKw < 0 || cooldown.isNegative() || reservationGrace.isNegative()) {
            throw new IllegalArgumentException("Umbrales de alerta no válidos");
        }
    }
}
