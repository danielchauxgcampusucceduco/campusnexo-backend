package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.CheckIn;
import edu.campusnexo.application.port.in.EstadoPuesto;
import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.InventarioUseCase.Hallazgo;
import edu.campusnexo.application.port.in.InventarioUseCase.ObservacionPuesto;
import edu.campusnexo.application.port.in.RegistroDePuestosUseCase;
import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.domain.activo.Cpu;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.persona.Docente;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.support.TestData;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DispositivoControllerTest {
    private final InventarioUseCase inventario = mock(InventarioUseCase.class);
    private final ReservationUseCase reservations = mock(ReservationUseCase.class);
    private final RegistroDePuestosUseCase registro = mock(RegistroDePuestosUseCase.class);
    private final DispositivoController controller = new DispositivoController(inventario, reservations, registro,
            java.time.Clock.fixed(TestData.NOW, java.time.ZoneOffset.UTC));

    @Test
    void aHeartbeatIsAcceptedAndAnswersTheWorkstationState() {
        PuestoDeTrabajo puesto = new PuestoDeTrabajo("301-A", 1, List.of(new Cpu("C-1", 120, false, TestData.NOW)));
        when(registro.latido("C-1", null)).thenReturn(new RegistroDePuestosUseCase.Latido("C-1",
                new EstadoPuesto(puesto, false, true, true, Set.of()), null));
        when(registro.latido("PC-09", "301-A")).thenReturn(new RegistroDePuestosUseCase.Latido("PC-09", null,
                new EmparejamientoCpu.Ventana(TestData.NOW, TestData.NOW.plusSeconds(5))));

        var response = controller.latido(new DispositivoController.LatidoRequest("C-1", null));
        var unpaired = controller.latido(new DispositivoController.LatidoRequest("PC-09", "301-A"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(true, response.getBody().asignado());
        assertEquals(true, response.getBody().puesto().cpuSinUsuario());
        assertEquals(false, unpaired.getBody().asignado());
        assertEquals(TestData.NOW, unpaired.getBody().patronDesde());
    }

    @Test
    void theKioskCheckInShowsWhoseBookingItIs() {
        Reservation booking = new Reservation(UUID.randomUUID(), "301-A", TestData.NOW, TestData.NOW.plusSeconds(7200),
                20, "IS-701", "ana", "D-100", TestData.NOW);
        when(reservations.checkIn("301-A", "D-100"))
                .thenReturn(new CheckIn(booking, new Docente("D-100", "Marta Ruiz", "Software")));

        var response = controller.checkIn("301-A", new DispositivoController.CheckInRequest("D-100"));

        assertEquals("Marta Ruiz", response.nombre());
        assertEquals(TipoPersona.DOCENTE, response.tipo());
        assertEquals(TestData.NOW, response.checkedInAt());
    }

    @Test
    void theEdgeAuditListsWhatTheInventoryMisses() {
        when(inventario.registrarAuditoria("301-A", List.of(new ObservacionPuesto("301-A-P07", false, true))))
                .thenReturn(List.of(new Hallazgo("301-A-P07", TipoActivo.SILLA, "301A-SILLA-07")));

        var hallazgos = controller.auditoria(new DispositivoController.AuditoriaRequest("301-A",
                List.of(new DispositivoController.ObservacionRequest("301-A-P07", false, true))));

        assertEquals("301A-SILLA-07", hallazgos.getFirst().codigoActivo());
    }
}
