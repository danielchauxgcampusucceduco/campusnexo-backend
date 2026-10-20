package edu.campusnexo.domain.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryTest {
    private static final Instant AT = Instant.parse("2026-10-20T15:00:00Z");

    @Test
    @DisplayName("Una lectura válida se construye")
    void validReading() {
        assertDoesNotThrow(() -> new Telemetry("AULA-301", AT, 18, 0, 21, 820, 23.1, 58, 410, 2.4));
    }

    @Test
    @DisplayName("Los rangos físicos se validan en un único lugar y nombran el campo")
    void rejectsOutOfRangeValues() {
        DomainValidationException humidity = assertThrows(DomainValidationException.class,
                () -> new Telemetry("AULA-301", AT, 18, 0, 21, 820, 23.1, 120, 410, 2.4));
        assertEquals("humidityPct debe estar entre 0 y 100", humidity.getMessage());
        assertThrows(DomainValidationException.class,
                () -> new Telemetry("AULA-301", AT, -1, 0, 21, 820, 23.1, 58, 410, 2.4));
        assertThrows(DomainValidationException.class,
                () -> new Telemetry("AULA-301", AT, 18, 0, 21, Double.NaN, 23.1, 58, 410, 2.4));
        assertThrows(DomainValidationException.class,
                () -> new Telemetry("AULA-301", AT, 18, 0, 21, 820, 75, 58, 410, 2.4));
    }
}
