package edu.campusnexo.infrastructure.adapter.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.EnvironmentReading;
import java.time.Instant;

/**
 * JSON published by the room Arduino ({@code tools/arduino/campusnexo_salon/campusnexo_salon.ino}) on
 * {@code ucc/pasto/bloque_a/piso_2/{space_id}/ambiente}. Also accepted over HTTP by
 * {@code POST /api/v1/twin/environment}. The board usually has no real-time clock, so {@code timestamp}
 * is optional and the reception time is used instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArduinoEnvironmentPayload(
        @JsonProperty("space_id") String spaceId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("temperature_c") Double temperatureC,
        @JsonProperty("humidity_pct") Double humidityPct,
        @JsonProperty("co2_ppm") Double co2Ppm,
        @JsonProperty("illuminance_lux") Double illuminanceLux,
        @JsonProperty("lights_on") Boolean lightsOn,
        @JsonProperty("devices_on") Integer devicesOn) {

    /**
     * @param expectedSpaceId space taken from the MQTT topic, or {@code null} over HTTP; it must match
     *                        {@code space_id} when both are present
     * @param receivedAt      used when the payload has no timestamp
     */
    public EnvironmentReading toReading(String expectedSpaceId, Instant receivedAt) {
        String effectiveSpace = spaceId == null || spaceId.isBlank() ? expectedSpaceId : spaceId;
        if (expectedSpaceId != null && !expectedSpaceId.equals(effectiveSpace)) {
            throw new DomainValidationException("space_id del mensaje (" + spaceId + ") no coincide con el tópico ("
                    + expectedSpaceId + ")");
        }
        if (temperatureC == null || humidityPct == null || lightsOn == null) {
            throw new DomainValidationException("temperature_c, humidity_pct y lights_on son obligatorios");
        }
        return new EnvironmentReading(effectiveSpace, timestamp == null ? receivedAt : timestamp, temperatureC,
                humidityPct, co2Ppm, illuminanceLux == null ? 0 : illuminanceLux, lightsOn,
                devicesOn == null ? 0 : devicesOn);
    }
}
