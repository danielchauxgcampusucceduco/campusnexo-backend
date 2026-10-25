package edu.campusnexo.infrastructure.adapter.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ArduinoEnvironmentPayloadTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void fillsTopicIdentityAndReceptionTimeWhenTheBoardOmitsThem() {
        ArduinoEnvironmentPayload payload = new ArduinoEnvironmentPayload(null, null,
                22.0, 55.0, null, null, true, null);

        var reading = payload.toReading("421-A", RECEIVED_AT);

        assertEquals("421-A", reading.spaceId());
        assertEquals(RECEIVED_AT, reading.capturedAt());
        assertEquals(0.0, reading.illuminanceLux());
        assertEquals(0, reading.devicesOn());
    }

    @Test
    void rejectsIdentityMismatchAndMissingRequiredSensors() {
        ArduinoEnvironmentPayload mismatched = new ArduinoEnvironmentPayload("422-A", null,
                22.0, 55.0, null, null, true, 0);
        ArduinoEnvironmentPayload incomplete = new ArduinoEnvironmentPayload("421-A", null,
                22.0, 55.0, null, null, null, 0);

        assertThrows(DomainValidationException.class, () -> mismatched.toReading("421-A", RECEIVED_AT));
        assertThrows(DomainValidationException.class, () -> incomplete.toReading("421-A", RECEIVED_AT));
    }
}
