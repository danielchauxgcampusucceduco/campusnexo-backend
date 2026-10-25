package edu.campusnexo.domain.activo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CpuTest {
    private static final Instant NOW = TestData.NOW;
    private static final Duration VIGENCIA = Duration.ofMinutes(3);

    @Test
    @DisplayName("Un computador está encendido mientras su latido es reciente")
    void onWhileTheHeartbeatIsRecent() {
        Cpu never = new Cpu("301A-CPU-01", 120, false, null);
        assertFalse(never.encendida(NOW, VIGENCIA));
        Cpu beating = never.conLatido(NOW.minusSeconds(60));
        assertTrue(beating.encendida(NOW, VIGENCIA));
        assertFalse(beating.encendida(NOW.plus(Duration.ofMinutes(5)), VIGENCIA));
    }

    @Test
    @DisplayName("Un latido que llega tarde no retrocede el último")
    void lateHeartbeatsAreIgnored() {
        Cpu latest = new Cpu("301A-CPU-01", 120, false, NOW);
        assertSame(latest, latest.conLatido(NOW.minusSeconds(30)));
        assertEquals(NOW.plusSeconds(60), latest.conLatido(NOW.plusSeconds(60)).ultimoLatido());
    }

    @Test
    @DisplayName("Solo el computador consume energía; los muebles no")
    void onlyTheComputerDrawsPower() {
        assertEquals(120, Activo.registrar(TipoActivo.CPU, "C-1", 120, false).potenciaNominalW());
        assertEquals(0, Activo.registrar(TipoActivo.SILLA, "S-1", 0, false).potenciaNominalW());
        assertInstanceOf(Mesa.class, Activo.registrar(TipoActivo.MESA, "M-1", 0, true));
        assertFalse(Activo.registrar(TipoActivo.MESA, "M-1", 0, true).operativo());
        assertThrows(DomainValidationException.class, () -> Activo.registrar(TipoActivo.CPU, "C-2", 0, false));
    }
}
