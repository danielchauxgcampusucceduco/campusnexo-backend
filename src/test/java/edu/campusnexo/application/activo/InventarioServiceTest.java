package edu.campusnexo.application.activo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.EstadoPuesto;
import edu.campusnexo.application.port.in.InventarioUseCase.Hallazgo;
import edu.campusnexo.application.port.in.InventarioUseCase.ObservacionPuesto;
import edu.campusnexo.application.port.in.RegistrarActivoCommand;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.Sensing;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.ZoneDeskState;
import edu.campusnexo.support.InMemoryActivoRepository;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventarioServiceTest {
    private final CampusSpace lab = TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30);
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final InMemorySpaceRepositoryPort twins = new InMemorySpaceRepositoryPort();
    private final InMemoryAlertRepository alerts = new InMemoryAlertRepository();
    private final InventarioService service = new InventarioService(clock,
            new SpaceCatalogService(new InMemorySpaceCatalogRepository(lab)), new InMemoryActivoRepository(), twins,
            new AlertRecorder(alerts), Duration.ofMinutes(3), Duration.ofMinutes(30));

    @BeforeEach
    void inventoryOfTwoWorkstations() {
        for (int seat = 1; seat <= 2; seat++) {
            service.registrarActivo(new RegistrarActivoCommand("301-A", seat, TipoActivo.MESA, "M-" + seat, 0, false));
            service.registrarActivo(new RegistrarActivoCommand("301-A", seat, TipoActivo.SILLA, "S-" + seat, 0, false));
            service.registrarActivo(new RegistrarActivoCommand("301-A", seat, TipoActivo.CPU, "C-" + seat, 120, false));
        }
    }

    private void twin(int people, int seatedAtFirstDesk) {
        SpaceTwinState state = SpaceTwinState.initial(lab, clock.instant()).toBuilder()
                .occupancy(new OccupancyBelief(people, 0.5, clock.instant()))
                .sensing(new Sensing(true, clock.instant(), null, false))
                .desks(List.of(new DeskObservation("301-A-P01", seatedAtFirstDesk > 0 ? ZoneDeskState.OCUPADO_VERIFICADO
                        : ZoneDeskState.DISPONIBLE, seatedAtFirstDesk, 0, null, clock.instant())))
                .build();
        twins.overwrite(state);
    }

    @Test
    @DisplayName("Lista los puestos con lo que ve la cámara y lo que reporta cada computador")
    void workstationsWithCameraAndHeartbeat() {
        twin(1, 1);
        service.registrarLatido("C-1");
        List<EstadoPuesto> puestos = service.puestos("301-A");
        assertEquals(2, puestos.size());
        assertTrue(puestos.get(0).ocupado());
        assertTrue(puestos.get(0).cpuEncendida());
        assertFalse(puestos.get(0).cpuSinUsuario());
        assertFalse(puestos.get(1).cpuEncendida());
        assertTrue(puestos.get(1).faltantes().isEmpty());
    }

    @Test
    @DisplayName("Un computador encendido con el salón vacío levanta la alerta; con gente en el salón no")
    void computerLeftOnInAnEmptyRoom() {
        twin(3, 0);
        assertTrue(service.registrarLatido("C-1").cpuSinUsuario());
        assertTrue(alerts.rows().isEmpty(), "alguien pudo levantarse un momento");

        twin(0, 0);
        clock.advance(Duration.ofSeconds(60));
        service.registrarLatido("C-1");
        assertEquals(List.of(AlertType.CPU_ENCENDIDA_SIN_USUARIO), alerts.rows().stream().map(a -> a.type()).toList());
        assertThrows(ResourceNotFoundException.class, () -> service.registrarLatido("C-99"));
    }

    @Test
    @DisplayName("La auditoría de la cámara señala la silla que el inventario espera y no se ve")
    void auditFindsMissingFurniture() {
        List<Hallazgo> hallazgos = service.registrarAuditoria("301-A", List.of(
                new ObservacionPuesto("301-A-P01", true, true), new ObservacionPuesto("301-A-P02", false, true)));
        assertEquals(List.of(new Hallazgo("301-A-P02", TipoActivo.SILLA, "S-2")), hallazgos);
        assertEquals(AlertType.ACTIVO_FALTANTE, alerts.rows().getFirst().type());
    }

    @Test
    @DisplayName("Un activo solo se ubica en un puesto que exista en la rejilla del salón")
    void seatMustExist() {
        assertThrows(DomainValidationException.class, () -> service.registrarActivo(
                new RegistrarActivoCommand("301-A", 31, TipoActivo.MESA, "M-31", 0, false)));
        service.eliminarActivo("M-2");
        assertEquals(java.util.Set.of(TipoActivo.MESA), service.puestos("301-A").get(1).faltantes());
        assertThrows(ResourceNotFoundException.class, () -> service.eliminarActivo("M-2"));
    }
}
