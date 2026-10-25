package edu.campusnexo.application.activo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.RegistroDePuestosUseCase;
import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos;
import edu.campusnexo.domain.activo.PuestoPropuesto;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.InMemoryActivoRepository;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.InMemoryRegistroDePuestosRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegistroDePuestosServiceTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final CampusCatalogUseCase catalog = new SpaceCatalogService(new InMemorySpaceCatalogRepository(
            TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30)));
    private final InMemoryActivoRepository activos = new InMemoryActivoRepository();
    private final InventarioService inventario = new InventarioService(clock, catalog, activos,
            new InMemorySpaceRepositoryPort(), new AlertRecorder(new InMemoryAlertRepository()), Duration.ofMinutes(3),
            Duration.ofMinutes(30));
    private final RegistroDePuestosService service = new RegistroDePuestosService(clock, catalog, activos,
            new InMemoryRegistroDePuestosRepository(), inventario, Duration.ofSeconds(75), Duration.ofSeconds(5), 120);

    private void discover() {
        service.recibirPropuesta(new PropuestaDePuestos("301-A", clock.instant(), true, List.of(
                new PuestoPropuesto(1, 1, true, true, true, null),
                new PuestoPropuesto(1, 4, true, false, true, null))));
    }

    @Test
    @DisplayName("Descubrir, emparejar y confirmar: el inventario nace de la cámara")
    void discoverPairAndConfirm() {
        assertNull(service.latido("PC-01", "301-A").patron(), "sin emparejamiento el agente solo queda registrado");
        service.latido("PC-04", "301-A");
        discover();

        EmparejamientoCpu emparejamiento = service.iniciarEmparejamiento("301-A");
        assertEquals(List.of("PC-01", "PC-04"), emparejamiento.cpus());
        EmparejamientoCpu.Ventana slot = service.latido("PC-04", "301-A").patron();
        assertNotNull(slot, "el latido le dice al agente cuándo mostrar su patrón");
        service.patronVisto("301-A", 1, 4, slot.desde().plusMillis(500));

        RegistroDePuestosUseCase.Revision revision = service.revisar("301-A");
        assertEquals(List.of("PC-01"), revision.cpusSinPuesto());
        assertEquals("A4", revision.incompletos().getFirst().etiqueta());

        List<PropuestaDePuestos.Alta> altas = service.confirmar("301-A", Set.of());
        assertEquals(2, altas.size());
        assertEquals(Set.of(TipoActivo.SILLA), inventario.puestos("301-A").get(1).faltantes());
        assertTrue(service.latido("PC-04", "301-A").puesto().cpuEncendida(),
                "ya en su puesto, el latido pasa por el inventario");
        assertThrows(ResourceNotFoundException.class, () -> service.revisar("301-A"), "la propuesta se consumió");
    }

    @Test
    @DisplayName("Una nueva corrida conserva lo emparejado y solo propone lo que falta; descartar deja fuera un puesto")
    void rerunsAndDiscards() {
        discover();
        service.confirmar("301-A", Set.of("A4"));
        assertEquals(1, inventario.puestos("301-A").size());

        discover();
        assertEquals(List.of("A4"), service.revisar("301-A").altas().stream().map(PropuestaDePuestos.Alta::etiqueta)
                .toList());
    }

    @Test
    @DisplayName("Un patrón visto fuera de toda ranura o sin agentes no empareja nada")
    void patternOutsideAnySlot() {
        discover();
        assertThrows(DomainValidationException.class, () -> service.iniciarEmparejamiento("301-A"));
        service.latido("PC-01", "301-A");
        service.iniciarEmparejamiento("301-A");
        assertThrows(DomainValidationException.class, () -> service.patronVisto("301-A", 1, 1, clock.instant()));
        assertThrows(ResourceNotFoundException.class, () -> service.latido("PC-77", null));
    }
}
