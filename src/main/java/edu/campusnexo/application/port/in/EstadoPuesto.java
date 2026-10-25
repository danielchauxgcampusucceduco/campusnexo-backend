package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.activo.TipoActivo;
import java.util.Set;

/**
 * A workstation as the administration sees it: its assets, whether the camera sees someone at the seat,
 * whether its computer is on, and what it lacks ({@code faltantes}; a computer is expected in computer rooms).
 */
public record EstadoPuesto(PuestoDeTrabajo puesto, boolean ocupado, boolean cpuEncendida, boolean cpuSinUsuario,
                           Set<TipoActivo> faltantes) {
    public EstadoPuesto {
        faltantes = Set.copyOf(faltantes);
    }
}
