package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.EstadoPuesto;
import edu.campusnexo.domain.activo.Activo;
import edu.campusnexo.domain.activo.Cpu;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.activo.TipoActivo;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** A workstation with its assets and what the camera and its computer report about it. */
public record PuestoResponse(String codigo, String spaceId, int numero, boolean operativo, double potenciaNominalW,
                             boolean ocupado, boolean cpuEncendida, boolean cpuSinUsuario, Set<TipoActivo> faltantes,
                             List<ActivoResponse> activos) {

    public record ActivoResponse(String codigo, TipoActivo tipo, boolean fueraDeServicio, double potenciaW,
                                 Instant ultimoLatido) {
        static ActivoResponse from(Activo activo) {
            return new ActivoResponse(activo.codigo(), activo.tipo(), activo.fueraDeServicio(),
                    activo.potenciaNominalW(), activo instanceof Cpu cpu ? cpu.ultimoLatido() : null);
        }
    }

    public static PuestoResponse from(EstadoPuesto estado) {
        PuestoDeTrabajo puesto = estado.puesto();
        return new PuestoResponse(puesto.codigo(), puesto.spaceId(), puesto.numero(), puesto.operativo(),
                puesto.potenciaNominalW(), estado.ocupado(), estado.cpuEncendida(), estado.cpuSinUsuario(),
                estado.faltantes(),
                puesto.elementos().stream().filter(Activo.class::isInstance).map(e -> ActivoResponse.from((Activo) e))
                        .toList());
    }
}
