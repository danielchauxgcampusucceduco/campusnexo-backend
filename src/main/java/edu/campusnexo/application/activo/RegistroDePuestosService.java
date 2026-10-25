package edu.campusnexo.application.activo;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.RegistrarActivoCommand;
import edu.campusnexo.application.port.in.RegistroDePuestosUseCase;
import edu.campusnexo.domain.activo.Cpu;
import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos;
import edu.campusnexo.domain.activo.PropuestaDePuestos.Alta;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.activo.PuestoPropuesto;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ActivoRepository;
import edu.campusnexo.domain.port.RegistroDePuestosRepository;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RegistroDePuestosService implements RegistroDePuestosUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final ActivoRepository activos;
    private final RegistroDePuestosRepository registro;
    private final InventarioUseCase inventario;
    private final Duration anticipacion;
    private final Duration ranura;
    private final double potenciaCpuW;

    /**
     * @param anticipacion time before the first pattern slot: at least one heartbeat interval, so every agent
     *                     learns its slot before it starts
     * @param potenciaCpuW nominal power given to a computer registered from the camera, until staff correct it
     */
    public RegistroDePuestosService(Clock clock, CampusCatalogUseCase catalog, ActivoRepository activos,
                                    RegistroDePuestosRepository registro, InventarioUseCase inventario,
                                    Duration anticipacion, Duration ranura, double potenciaCpuW) {
        this.clock = clock;
        this.catalog = catalog;
        this.activos = activos;
        this.registro = registro;
        this.inventario = inventario;
        this.anticipacion = anticipacion;
        this.ranura = ranura;
        this.potenciaCpuW = potenciaCpuW;
    }

    @Override
    public PropuestaDePuestos recibirPropuesta(PropuestaDePuestos propuesta) {
        CampusSpace space = catalog.getRequiredSpace(propuesta.spaceId());
        propuesta.validarRejilla(space.seatLayout());
        Map<String, String> pairedBefore = registro.findPropuesta(space.spaceId())
                .map(previous -> previous.puestos().stream().filter(p -> p.cpu() != null)
                        .collect(Collectors.toMap(PuestoPropuesto::etiqueta, PuestoPropuesto::cpu)))
                .orElse(Map.of());
        PropuestaDePuestos kept = new PropuestaDePuestos(propuesta.spaceId(), propuesta.recibidaAt(), propuesta.emulada(),
                propuesta.puestos().stream()
                        .map(p -> p.cpu() == null && pairedBefore.containsKey(p.etiqueta())
                                ? p.conCpu(pairedBefore.get(p.etiqueta())) : p)
                        .toList());
        registro.savePropuesta(kept);
        return kept;
    }

    @Override
    public Revision revisar(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        PropuestaDePuestos propuesta = pendiente(spaceId);
        return new Revision(propuesta, propuesta.altas(space.seatLayout(), activos.findPuestos(spaceId)),
                propuesta.incompletos(), sinPuesto(spaceId, propuesta),
                registro.findEmparejamiento(spaceId).orElse(null));
    }

    @Override
    public List<Alta> confirmar(String spaceId, Set<String> descartar) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        PropuestaDePuestos propuesta = pendiente(spaceId);
        List<Alta> altas = propuesta.altas(space.seatLayout(), activos.findPuestos(spaceId)).stream()
                .filter(alta -> !descartar.contains(alta.etiqueta()))
                .toList();
        for (Alta alta : altas) {
            for (TipoActivo tipo : alta.agregar()) {
                String codigo = tipo == TipoActivo.CPU ? alta.cpu() : PropuestaDePuestos.codigoDe(spaceId, tipo, alta.numero());
                inventario.registrarActivo(new RegistrarActivoCommand(spaceId, alta.numero(), tipo, codigo,
                        tipo == TipoActivo.CPU ? potenciaCpuW : 0, false));
            }
        }
        registro.deletePropuesta(spaceId);
        return altas;
    }

    @Override
    public EmparejamientoCpu iniciarEmparejamiento(String spaceId) {
        catalog.getRequiredSpace(spaceId);
        List<String> unpaired = sinPuesto(spaceId, registro.findPropuesta(spaceId).orElse(null));
        if (unpaired.isEmpty()) {
            throw new DomainValidationException("No hay computadores sin puesto que hayan reportado desde " + spaceId);
        }
        EmparejamientoCpu emparejamiento = new EmparejamientoCpu(spaceId, clock.instant().plus(anticipacion), ranura,
                unpaired);
        registro.saveEmparejamiento(emparejamiento);
        return emparejamiento;
    }

    @Override
    public PropuestaDePuestos patronVisto(String spaceId, int fila, int columna, Instant at) {
        PropuestaDePuestos propuesta = pendiente(spaceId);
        String cpu = registro.findEmparejamiento(spaceId)
                .flatMap(emparejamiento -> emparejamiento.cpuMostrando(at))
                .orElseThrow(() -> new DomainValidationException(
                        "Ningún computador de " + spaceId + " tenía que mostrar su patrón a las " + at));
        PropuestaDePuestos paired = propuesta.conCpu(fila, columna, cpu);
        registro.savePropuesta(paired);
        return paired;
    }

    @Override
    public Latido latido(String cpu, String spaceId) {
        if (activos.findPuestoByCpu(cpu).isPresent()) {
            return new Latido(cpu, inventario.registrarLatido(cpu), null);
        }
        if (spaceId == null || spaceId.isBlank()) {
            throw new ResourceNotFoundException("activo_not_found",
                    "Ningún puesto tiene el computador " + cpu + " y el agente no dijo en qué salón está");
        }
        catalog.getRequiredSpace(spaceId);
        registro.saveAgente(cpu, spaceId, clock.instant());
        EmparejamientoCpu.Ventana patron = registro.findEmparejamiento(spaceId)
                .flatMap(emparejamiento -> emparejamiento.ventanaDe(cpu))
                .filter(ventana -> ventana.hasta().isAfter(clock.instant()))
                .orElse(null);
        return new Latido(cpu, null, patron);
    }

    private PropuestaDePuestos pendiente(String spaceId) {
        return registro.findPropuesta(spaceId).orElseThrow(() -> new ResourceNotFoundException("propuesta_not_found",
                "No hay una propuesta de puestos pendiente para " + spaceId));
    }

    /** Computers whose agent reported from the room and that are neither in a seat nor already paired. */
    private List<String> sinPuesto(String spaceId, PropuestaDePuestos propuesta) {
        Set<String> enInventario = activos.findPuestos(spaceId).stream()
                .map(PuestoDeTrabajo::cpu).flatMap(Optional::stream).map(Cpu::codigo)
                .collect(Collectors.toSet());
        Set<String> emparejados = propuesta == null ? Set.of() : propuesta.puestos().stream()
                .map(PuestoPropuesto::cpu).filter(Objects::nonNull).collect(Collectors.toSet());
        return registro.findAgentes(spaceId).stream()
                .filter(cpu -> !enInventario.contains(cpu) && !emparejados.contains(cpu))
                .toList();
    }
}
