package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.RegistrarActivoCommand;
import edu.campusnexo.domain.activo.TipoActivo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Places an asset at seat {@code numeroPuesto} of the space. {@code potenciaW} is required for a computer. */
public record ActivoRequest(
        @NotBlank @Size(max = 32) String spaceId,
        @NotNull @Min(1) Integer numeroPuesto,
        @NotNull TipoActivo tipo,
        @NotBlank @Size(max = 40) String codigo,
        @DecimalMin("0") @DecimalMax("2000") Double potenciaW,
        Boolean fueraDeServicio) {

    public RegistrarActivoCommand toCommand() {
        return new RegistrarActivoCommand(spaceId, numeroPuesto, tipo, codigo, potenciaW == null ? 0 : potenciaW,
                Boolean.TRUE.equals(fueraDeServicio));
    }
}
