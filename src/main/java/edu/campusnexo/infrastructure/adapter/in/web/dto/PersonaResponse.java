package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.persona.TipoPersona;
import java.util.Arrays;
import java.util.List;

/** {@code puedeReservar} and {@code reservaMaximaHoras} are the person's own booking rules. */
public record PersonaResponse(TipoPersona tipo, String codigo, String nombre, String adscripcion,
                              List<SpaceType> puedeReservar, long reservaMaximaHoras) {

    public static PersonaResponse from(Persona persona) {
        return new PersonaResponse(persona.tipo(), persona.codigo(), persona.nombre(), persona.adscripcion(),
                Arrays.stream(SpaceType.values()).filter(persona::puedeReservar).toList(),
                persona.reservaMaxima().toHours());
    }
}
