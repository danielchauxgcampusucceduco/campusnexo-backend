package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.activo.TipoActivo;

/** {@code potenciaW} only matters for a computer. */
public record RegistrarActivoCommand(String spaceId, int numeroPuesto, TipoActivo tipo, String codigo,
                                     double potenciaW, boolean fueraDeServicio) {
}
