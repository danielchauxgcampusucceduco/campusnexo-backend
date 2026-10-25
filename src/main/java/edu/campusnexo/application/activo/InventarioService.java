package edu.campusnexo.application.activo;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.EstadoPuesto;
import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.RegistrarActivoCommand;
import edu.campusnexo.domain.activo.Activo;
import edu.campusnexo.domain.activo.ElementoDeInventario;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.port.ActivoRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.DeskObservation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Joins the inventory with the camera: the twin knows which seats have someone, the inventory which computer
 * sits at each seat, and the heartbeats which computers are on. A computer that keeps reporting while the
 * room is empty was left on after class.
 */
public class InventarioService implements InventarioUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final ActivoRepository activos;
    private final SpaceRepositoryPort twins;
    private final AlertRecorder alerts;
    private final Duration vigenciaLatido;
    private final Duration cooldown;

    public InventarioService(Clock clock, CampusCatalogUseCase catalog, ActivoRepository activos,
                             SpaceRepositoryPort twins, AlertRecorder alerts, Duration vigenciaLatido,
                             Duration cooldown) {
        this.clock = clock;
        this.catalog = catalog;
        this.activos = activos;
        this.twins = twins;
        this.alerts = alerts;
        this.vigenciaLatido = vigenciaLatido;
        this.cooldown = cooldown;
    }

    @Override
    public void registrarActivo(RegistrarActivoCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        if (space.seatLayout() == null || command.numeroPuesto() < 1
                || command.numeroPuesto() > space.seatLayout().seats()) {
            throw new DomainValidationException("El espacio " + space.spaceId() + " no tiene el puesto "
                    + command.numeroPuesto());
        }
        activos.save(space.spaceId(), command.numeroPuesto(), Activo.registrar(command.tipo(), command.codigo(),
                command.potenciaW(), command.fueraDeServicio()));
    }

    @Override
    public List<EstadoPuesto> puestos(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        Map<String, DeskObservation> desks = twins.findTwin(spaceId)
                .map(twin -> twin.desks().stream()
                        .collect(Collectors.toMap(DeskObservation::zoneId, Function.identity(), (a, b) -> b)))
                .orElse(Map.of());
        return activos.findPuestos(spaceId).stream()
                .map(puesto -> estado(space, puesto, desks.get(puesto.codigo()), now))
                .toList();
    }

    @Override
    public void eliminarActivo(String codigo) {
        if (!activos.delete(codigo)) {
            throw new ResourceNotFoundException("activo_not_found", "No existe el activo " + codigo);
        }
    }

    @Override
    public EstadoPuesto registrarLatido(String codigoCpu) {
        Instant now = clock.instant();
        PuestoDeTrabajo puesto = activos.findPuestoByCpu(codigoCpu)
                .orElseThrow(() -> new ResourceNotFoundException("activo_not_found",
                        "Ningún puesto tiene el computador " + codigoCpu))
                .conLatido(now);
        activos.saveLatido(codigoCpu, now);
        CampusSpace space = catalog.getRequiredSpace(puesto.spaceId());
        SpaceTwinState twin = twins.findTwin(space.spaceId()).orElse(null);
        DeskObservation desk = twin == null ? null : twin.desks().stream()
                .filter(d -> d.zoneId().equals(puesto.codigo())).findFirst().orElse(null);
        EstadoPuesto estado = estado(space, puesto, desk, now);
        // Someone may have just stepped away from the seat: only an empty room means the computer was left on.
        boolean roomEmpty = twin != null && twin.cameraOnline() && twin.occupancy().people() == 0;
        if (estado.cpuSinUsuario() && roomEmpty) {
            Alert alert = Alert.raise(space.spaceId(), AlertType.CPU_ENCENDIDA_SIN_USUARIO, AlertSeverity.ADVERTENCIA,
                    now, "El computador " + codigoCpu + " del puesto " + puesto.codigo()
                            + " sigue encendido con el salón vacío", puesto.potenciaNominalW(), 0);
            alerts.recordUnlessRaisedSince(alert, now.minus(cooldown));
        }
        return estado;
    }

    @Override
    public List<Hallazgo> registrarAuditoria(String spaceId, List<ObservacionPuesto> observaciones) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        Map<String, PuestoDeTrabajo> puestos = activos.findPuestos(space.spaceId()).stream()
                .collect(Collectors.toMap(PuestoDeTrabajo::codigo, Function.identity()));
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (ObservacionPuesto observacion : observaciones) {
            PuestoDeTrabajo puesto = puestos.get(observacion.zoneId());
            if (puesto == null) {
                continue;
            }
            for (ElementoDeInventario elemento : puesto.elementos()) {
                if (elemento instanceof Activo activo && !seen(activo.tipo(), observacion)) {
                    hallazgos.add(new Hallazgo(puesto.codigo(), activo.tipo(), activo.codigo()));
                }
            }
        }
        if (!hallazgos.isEmpty()) {
            Hallazgo first = hallazgos.getFirst();
            alerts.recordUnlessRaisedSince(Alert.raise(space.spaceId(), AlertType.ACTIVO_FALTANTE,
                    AlertSeverity.INFORMATIVA, now, hallazgos.size() + " activos del inventario no se ven en sus puestos;"
                            + " el primero: " + first.tipo() + " " + first.codigoActivo() + " en " + first.puesto(),
                    hallazgos.size(), 0), now.minus(cooldown));
        }
        return hallazgos;
    }

    /** The camera sees furniture only; a computer under the table is checked by its heartbeat instead. */
    private static boolean seen(TipoActivo tipo, ObservacionPuesto observacion) {
        return switch (tipo) {
            case SILLA -> observacion.silla();
            case MESA -> observacion.mesa();
            case CPU -> true;
        };
    }

    private EstadoPuesto estado(CampusSpace space, PuestoDeTrabajo puesto, DeskObservation desk, Instant now) {
        boolean ocupado = desk != null && desk.peopleInside() > 0;
        boolean encendida = puesto.cpu().map(cpu -> cpu.encendida(now, vigenciaLatido)).orElse(false);
        return new EstadoPuesto(puesto, ocupado, encendida, puesto.cpuEncendidaSinUsuario(ocupado, now, vigenciaLatido),
                puesto.faltantes(space.type() == SpaceType.SALA_COMPUTO));
    }
}
