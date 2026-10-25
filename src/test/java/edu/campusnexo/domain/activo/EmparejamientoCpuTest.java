package edu.campusnexo.domain.activo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmparejamientoCpuTest {
    private static final Instant INICIO = TestData.NOW.plusSeconds(75);
    private final EmparejamientoCpu emparejamiento = new EmparejamientoCpu("301-A", INICIO, Duration.ofSeconds(5),
            List.of("PC-01", "PC-02", "PC-03"));

    @Test
    @DisplayName("Cada computador tiene su ranura, una tras otra")
    void slotsOneAfterTheOther() {
        assertEquals(new EmparejamientoCpu.Ventana(INICIO.plusSeconds(5), INICIO.plusSeconds(10)),
                emparejamiento.ventanaDe("PC-02").orElseThrow());
        assertTrue(emparejamiento.ventanaDe("PC-99").isEmpty());
        assertEquals(INICIO.plusSeconds(15), emparejamiento.fin());
    }

    @Test
    @DisplayName("La hora en que la cámara vio el patrón dice qué computador lo mostraba")
    void theMomentNamesTheComputer() {
        assertEquals(Optional.of("PC-01"), emparejamiento.cpuMostrando(INICIO));
        assertEquals(Optional.of("PC-03"), emparejamiento.cpuMostrando(INICIO.plusMillis(14_999)));
        assertTrue(emparejamiento.cpuMostrando(INICIO.minusMillis(1)).isEmpty());
        assertTrue(emparejamiento.cpuMostrando(INICIO.plusSeconds(15)).isEmpty());
    }

    @Test
    @DisplayName("La ranura es positiva y ningún computador se repite")
    void validation() {
        assertThrows(DomainValidationException.class,
                () -> new EmparejamientoCpu("301-A", INICIO, Duration.ZERO, List.of("PC-01")));
        assertThrows(DomainValidationException.class,
                () -> new EmparejamientoCpu("301-A", INICIO, Duration.ofSeconds(5), List.of("PC-01", "PC-01")));
    }
}
