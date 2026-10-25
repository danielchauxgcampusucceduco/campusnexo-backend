package edu.campusnexo.domain.activo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PuestoDeTrabajoTest {
    private static final Instant NOW = TestData.NOW;
    private static final Duration VIGENCIA = Duration.ofMinutes(3);

    private static PuestoDeTrabajo puesto(ElementoDeInventario... elementos) {
        return new PuestoDeTrabajo("301-A", 7, List.of(elementos));
    }

    @Test
    @DisplayName("El código del puesto es la región de cámara del asiento, así cámara e inventario se encuentran")
    void codeIsTheSeatRegion() {
        assertEquals("301-A-P07", puesto(new Mesa("M-7", false)).codigo());
    }

    @Test
    @DisplayName("Composite: el puesto suma la potencia de sus partes y es operativo solo si todas lo son")
    void compositeAddsItsParts() {
        PuestoDeTrabajo completo = puesto(new Mesa("M-7", false), new Silla("S-7", false), new Cpu("C-7", 120, false, null));
        assertEquals(120, completo.potenciaNominalW());
        assertTrue(completo.operativo());
        assertFalse(puesto(new Mesa("M-7", false), new Silla("S-7", true)).operativo());
        ElementoDeInventario anidado = new PuestoDeTrabajo("301-A", 8, List.of(completo, new Cpu("C-8", 80, false, null)));
        assertEquals(200, anidado.potenciaNominalW());
    }

    @Test
    @DisplayName("Lista lo que le falta; el computador solo se exige donde se pide")
    void missingParts() {
        PuestoDeTrabajo soloMesa = puesto(new Mesa("M-7", false));
        assertEquals(Set.of(TipoActivo.SILLA), soloMesa.faltantes(false));
        assertEquals(Set.of(TipoActivo.SILLA, TipoActivo.CPU), soloMesa.faltantes(true));
        assertFalse(soloMesa.operativo());
    }

    @Test
    @DisplayName("Reconoce el computador encendido en un puesto sin nadie")
    void computerOnWithoutUser() {
        PuestoDeTrabajo puesto = puesto(new Mesa("M-7", false), new Silla("S-7", false),
                new Cpu("C-7", 120, false, null)).conLatido(NOW.minusSeconds(30));
        assertTrue(puesto.cpuEncendidaSinUsuario(false, NOW, VIGENCIA));
        assertFalse(puesto.cpuEncendidaSinUsuario(true, NOW, VIGENCIA));
        assertFalse(puesto.cpuEncendidaSinUsuario(false, NOW.plus(Duration.ofMinutes(10)), VIGENCIA));
        assertFalse(puesto(new Mesa("M-7", false)).cpuEncendidaSinUsuario(false, NOW, VIGENCIA));
    }

    @Test
    @DisplayName("Un activo no puede estar dos veces en el mismo puesto")
    void noDuplicateAssets() {
        assertThrows(DomainValidationException.class, () -> puesto(new Mesa("M-7", false), new Mesa("M-7", false)));
        assertThrows(DomainValidationException.class, () -> new PuestoDeTrabajo("301-A", 0, List.of()));
    }
}
