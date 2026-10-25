package edu.campusnexo.domain.activo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PropuestaDePuestosTest {
    private static final SeatLayout REJILLA = new SeatLayout(5, 6);

    private static PropuestaDePuestos propuesta(PuestoPropuesto... puestos) {
        return new PropuestaDePuestos("301-A", TestData.NOW, true, List.of(puestos));
    }

    @Test
    @DisplayName("Cada puesto propuesto se nombra por fila y columna: A1 es el primero junto al tablero")
    void labels() {
        assertEquals("A1", new PuestoPropuesto(1, 1, true, true, true, null).etiqueta());
        assertEquals("E6", new PuestoPropuesto(5, 6, true, true, true, null).etiqueta());
        assertTrue(new PuestoPropuesto(1, 4, true, false, true, null).incompleto());
    }

    @Test
    @DisplayName("Solo propone lo que el inventario aún no tiene en ese asiento")
    void proposesOnlyDifferences() {
        PropuestaDePuestos propuesta = propuesta(new PuestoPropuesto(1, 1, true, true, true, "PC-01"),
                new PuestoPropuesto(1, 4, true, false, true, null));
        List<PuestoDeTrabajo> inventario = List.of(new PuestoDeTrabajo("301-A", 1, List.of(new Mesa("301A-MESA-01", false))));

        List<PropuestaDePuestos.Alta> altas = propuesta.altas(REJILLA, inventario);

        assertEquals(new PropuestaDePuestos.Alta("A1", 1, Set.of(TipoActivo.SILLA, TipoActivo.CPU), "PC-01"), altas.get(0));
        assertEquals(new PropuestaDePuestos.Alta("A4", 4, Set.of(TipoActivo.MESA), null), altas.get(1));
        assertEquals(List.of(propuesta.puestos().get(1)), propuesta.incompletos());
        assertEquals("301A-SILLA-07", PropuestaDePuestos.codigoDe("301-A", TipoActivo.SILLA, 7));
    }

    @Test
    @DisplayName("Emparejar un computador lo mueve al puesto donde se vio su patrón")
    void pairingMovesTheComputer() {
        PropuestaDePuestos propuesta = propuesta(new PuestoPropuesto(1, 1, true, true, true, "PC-01"),
                new PuestoPropuesto(1, 2, true, true, true, null));
        PropuestaDePuestos emparejada = propuesta.conCpu(1, 2, "PC-01");
        assertNull(emparejada.puestos().get(0).cpu());
        assertEquals("PC-01", emparejada.puestos().get(1).cpu());
        assertThrows(DomainValidationException.class, () -> propuesta.conCpu(5, 6, "PC-09"));
    }

    @Test
    @DisplayName("La cámara no puede inventar filas ni repetir puestos")
    void gridAndDuplicates() {
        assertThrows(DomainValidationException.class,
                () -> propuesta(new PuestoPropuesto(6, 1, true, true, true, null)).validarRejilla(REJILLA));
        assertThrows(DomainValidationException.class, () -> propuesta(new PuestoPropuesto(1, 1, true, true, true, null),
                new PuestoPropuesto(1, 1, true, true, true, null)));
        assertThrows(DomainValidationException.class, () -> propuesta());
    }
}
