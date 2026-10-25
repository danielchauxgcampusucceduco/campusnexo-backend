package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.persona.TipoPersona;
import java.util.List;

/** The administration registers the teachers and students that bookings and check-ins name. */
public interface PersonaAdminUseCase {
    /** Registers the person or updates the one with the same institutional code. */
    Persona registrar(RegistrarPersonaCommand command);

    /** People of the given kind, or everyone when {@code tipo} is {@code null}. */
    List<Persona> listar(TipoPersona tipo);

    Persona obtener(String codigo);

    void eliminar(String codigo);
}
