package edu.campusnexo.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.exception.SpaceNotFoundException;
import edu.campusnexo.application.port.in.EspacioAdminUseCase.Medidas;
import edu.campusnexo.domain.activo.Activo;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.InMemoryActivoRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EspacioAdminServiceTest {
    private final InMemorySpaceCatalogRepository catalog = new InMemorySpaceCatalogRepository(
            TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30));
    private final InMemoryActivoRepository activos = new InMemoryActivoRepository();
    private final EspacioAdminService service = new EspacioAdminService(catalog, activos);

    @Test
    @DisplayName("Las medidas tomadas en sitio reemplazan las ilustrativas del salón piloto")
    void adjustsThePilotRoom() {
        var ajustado = service.ajustar("301-A", new Medidas(28, 58.5, 4, 7, 28, true));

        assertEquals(28, catalog.findById("301-A").orElseThrow().capacity());
        assertEquals(58.5, ajustado.areaSqm());
        assertEquals(28, ajustado.seatLayout().seats());
        assertEquals(SpaceType.SALA_COMPUTO, ajustado.type(), "el tipo no se ajusta");
    }

    @Test
    @DisplayName("Un salón puede quedar sin rejilla de puestos, pero filas y columnas van juntas")
    void seatGridIsAllOrNothing() {
        assertNull(service.ajustar("301-A", new Medidas(30, 60, null, null, 0, true)).seatLayout());
        assertThrows(DomainValidationException.class,
                () -> service.ajustar("301-A", new Medidas(30, 60, 5, null, 30, true)));
        assertThrows(DomainValidationException.class,
                () -> service.ajustar("301-A", new Medidas(0, 60, 5, 6, 30, true)));
    }

    @Test
    @DisplayName("No deja puestos registrados fuera de la nueva rejilla")
    void keepsRegisteredWorkstationsInsideTheGrid() {
        activos.save("301-A", 30, Activo.registrar(TipoActivo.SILLA, "301A-SILLA-30", 0, false));

        var error = assertThrows(ConflictException.class,
                () -> service.ajustar("301-A", new Medidas(24, 50, 4, 6, 24, true)));

        assertEquals("puestos_fuera_de_rejilla", error.code());
        assertEquals(30, catalog.findById("301-A").orElseThrow().capacity(), "nada cambia");
    }

    @Test
    @DisplayName("Un salón que no existe no se crea al ajustarlo")
    void unknownRoom() {
        assertThrows(SpaceNotFoundException.class, () -> service.ajustar("999-Z", new Medidas(10, 20, null, null, 0, false)));
    }
}
