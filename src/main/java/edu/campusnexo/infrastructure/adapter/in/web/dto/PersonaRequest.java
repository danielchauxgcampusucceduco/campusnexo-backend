package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.RegistrarPersonaCommand;
import edu.campusnexo.domain.persona.TipoPersona;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code adscripcion} is the department of a teacher or the program of a student. No contact data is accepted. */
public record PersonaRequest(
        @NotNull TipoPersona tipo,
        @NotBlank @Size(max = 32) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Size(max = 120) String adscripcion) {

    public RegistrarPersonaCommand toCommand() {
        return new RegistrarPersonaCommand(tipo, codigo, nombre, adscripcion);
    }
}
