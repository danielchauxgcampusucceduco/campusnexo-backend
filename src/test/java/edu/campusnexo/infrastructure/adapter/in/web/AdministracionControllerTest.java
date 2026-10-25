package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.EspacioAdminUseCase;
import edu.campusnexo.application.port.in.EstadoPuesto;
import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.PersonaAdminUseCase;
import edu.campusnexo.application.port.in.RegistrarActivoCommand;
import edu.campusnexo.domain.activo.Cpu;
import edu.campusnexo.domain.activo.Mesa;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.persona.Docente;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ActivoRequest;
import edu.campusnexo.infrastructure.adapter.in.web.dto.PersonaRequest;
import edu.campusnexo.support.TestData;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AdministracionControllerTest {
    private final PersonaAdminUseCase personas = mock(PersonaAdminUseCase.class);
    private final InventarioUseCase inventario = mock(InventarioUseCase.class);
    private final EspacioAdminUseCase espacios = mock(EspacioAdminUseCase.class);
    private final AdministracionController controller = new AdministracionController(personas, inventario,
            mock(edu.campusnexo.application.port.in.RegistroDePuestosUseCase.class), espacios);

    @Test
    void adjustsARoomAndAnswersWithItsSeatGrid() {
        var room = TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30);
        when(espacios.ajustar(any(), any())).thenReturn(room.conMedidas(28, 58.5, new SeatLayout(4, 7), 28, true));

        var response = controller.ajustarEspacio("301-A",
                new AdministracionController.EspacioRequest(28, 58.5, 4, 7, 28, true));

        assertEquals(28, response.capacity());
        assertEquals(4, response.seatRows());
        assertEquals(7, response.seatColumns());
        verify(espacios).ajustar("301-A", new EspacioAdminUseCase.Medidas(28, 58.5, 4, 7, 28, true));
    }

    @Test
    void registersAPersonAndAnswersWithItsOwnBookingRules() {
        when(personas.registrar(any())).thenReturn(new Docente("D-100", "Marta Ruiz", "Software"));

        var response = controller.registrarPersona(new PersonaRequest(TipoPersona.DOCENTE, "D-100", "Marta Ruiz", "Software"));

        assertEquals(TipoPersona.DOCENTE, response.tipo());
        assertEquals(4, response.reservaMaximaHoras());
        assertEquals(List.of(SpaceType.AULA, SpaceType.SALA_ESTUDIO, SpaceType.AUDITORIO, SpaceType.LABORATORIO,
                SpaceType.SALA_COMPUTO), response.puedeReservar());
        assertEquals(HttpStatus.NO_CONTENT, controller.eliminarPersona("D-100").getStatusCode());
        verify(personas).eliminar("D-100");
    }

    @Test
    void listsWorkstationsWithTheirAssetsAndRegistersNewOnes() {
        PuestoDeTrabajo puesto = new PuestoDeTrabajo("301-A", 7, List.of(new Mesa("M-7", false),
                new Cpu("C-7", 120, false, null)));
        when(inventario.puestos("301-A")).thenReturn(List.of(new EstadoPuesto(puesto, false, true, true,
                Set.of(TipoActivo.SILLA))));

        var puestos = controller.puestos("301-A");
        var created = controller.registrarActivo(new ActivoRequest("301-A", 7, TipoActivo.SILLA, "S-7", null, null));

        assertEquals("301-A-P07", puestos.getFirst().codigo());
        assertEquals(120, puestos.getFirst().potenciaNominalW());
        assertEquals(2, puestos.getFirst().activos().size());
        assertEquals(Set.of(TipoActivo.SILLA), puestos.getFirst().faltantes());
        assertEquals(HttpStatus.NO_CONTENT, created.getStatusCode());
        verify(inventario).registrarActivo(new RegistrarActivoCommand("301-A", 7, TipoActivo.SILLA, "S-7", 0, false));
    }
}
