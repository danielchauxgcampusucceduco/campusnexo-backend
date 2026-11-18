package edu.campusnexo.domain.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryTest {
    private static final Instant AT = Instant.parse("2026-10-20T15:00:00Z");

    @Test
    @DisplayName("Una lectura válida se construye")
    void validReading() {
        assertDoesNotThrow(() -> new Telemetry("AULA-301", AT, 18, 0, 21, null));
    }

    @Test
    @DisplayName("Los rangos se validan en un único lugar y nombran el campo")
    void rejectsOutOfRangeValues() {
        DomainValidationException wifi = assertThrows(DomainValidationException.class,
                () -> new Telemetry("AULA-301", AT, 18, 0, -1, null));
        assertTrue(wifi.getMessage().startsWith("wifiDevices"));
        assertThrows(DomainValidationException.class,
                () -> new Telemetry("AULA-301", AT, -1, 0, 21, null));
        assertThrows(DomainValidationException.class,
                () -> new Telemetry("AULA-301", AT, 18, 0, 21, -2));
        assertDoesNotThrow(() -> new Telemetry("AULA-301", AT, 18, 0, null, null), "sin Wi-Fi ni conteo propio");
    }
}
