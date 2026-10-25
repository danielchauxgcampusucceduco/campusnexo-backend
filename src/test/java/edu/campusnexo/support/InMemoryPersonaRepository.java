package edu.campusnexo.support;

import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.domain.port.PersonaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPersonaRepository implements PersonaRepository {
    private final Map<String, Persona> rows = new ConcurrentHashMap<>();

    @Override
    public Persona save(Persona persona) {
        rows.put(persona.codigo(), persona);
        return persona;
    }

    @Override
    public Optional<Persona> findByCodigo(String codigo) {
        return codigo == null ? Optional.empty() : Optional.ofNullable(rows.get(codigo.strip()));
    }

    @Override
    public List<Persona> findAll(TipoPersona tipo) {
        return rows.values().stream().filter(p -> tipo == null || p.tipo() == tipo)
                .sorted(Comparator.comparing(Persona::nombre)).toList();
    }

    @Override
    public boolean delete(String codigo) {
        return rows.remove(codigo) != null;
    }
}
