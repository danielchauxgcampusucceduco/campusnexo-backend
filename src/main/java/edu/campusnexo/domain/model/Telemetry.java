package edu.campusnexo.domain.model;

import java.time.Instant;

public record Telemetry(
        String spaceId, Instant recordedAt, int peopleCount, int objectCount,
        int wifiDevices, double co2Ppm, double temperatureC, double humidityPct,
        double illuminanceLux, double activePowerKw) {
    public Telemetry {
        if (spaceId == null || spaceId.isBlank()) throw new IllegalArgumentException("spaceId es obligatorio");
        if (peopleCount < 0 || objectCount < 0 || wifiDevices < 0) throw new IllegalArgumentException("Los conteos no pueden ser negativos");
        if (!Double.isFinite(co2Ppm) || co2Ppm < 0 || !Double.isFinite(temperatureC)
                || !Double.isFinite(humidityPct) || humidityPct < 0 || humidityPct > 100
                || !Double.isFinite(illuminanceLux) || illuminanceLux < 0
                || !Double.isFinite(activePowerKw) || activePowerKw < 0) {
            throw new IllegalArgumentException("Mediciones fuera de rango");
        }
        if (recordedAt == null) throw new IllegalArgumentException("recordedAt es obligatorio");
    }
}
