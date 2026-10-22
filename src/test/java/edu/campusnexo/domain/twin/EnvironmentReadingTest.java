package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EnvironmentReadingTest {

    @Test
    void allowsRoomsWithoutACo2Sensor() {
        EnvironmentReading reading = new EnvironmentReading(
                "AULA-301", Instant.parse("2026-10-21T15:00:00Z"), 22, 55, null, 300, false, 0);

        assertNull(reading.co2Ppm());
        assertEquals(22, reading.temperatureC());
    }

    @Test
    void rejectsHumidityOutsidePhysicalBounds() {
        assertThrows(DomainValidationException.class, () -> new EnvironmentReading(
                "AULA-301", Instant.now(), 22, 101, 500.0, 300, false, 0));
    }
}
