package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.persona.TipoPersona;

/** {@code adscripcion} is the department of a teacher or the program of a student. */
public record RegistrarPersonaCommand(TipoPersona tipo, String codigo, String nombre, String adscripcion) {
}
