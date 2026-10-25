package edu.campusnexo.domain.port;

import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.persona.TipoPersona;
import java.util.List;
import java.util.Optional;

public interface PersonaRepository {
    /** Stores the person, replacing the one with the same institutional code. */
    Persona save(Persona persona);

    Optional<Persona> findByCodigo(String codigo);

    /** People of the given kind, or everyone when {@code tipo} is {@code null}, ordered by name. */
    List<Persona> findAll(TipoPersona tipo);

    /** @return whether a person with that code existed */
    boolean delete(String codigo);
}
