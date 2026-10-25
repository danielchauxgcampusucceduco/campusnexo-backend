package edu.campusnexo.infrastructure.adapter.out.persistence.persona;

import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.domain.port.PersonaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaPersonaAdapter implements PersonaRepository {
    private final SpringDataPersonaRepository data;

    JpaPersonaAdapter(SpringDataPersonaRepository data) {
        this.data = data;
    }

    @Override
    @Transactional
    public Persona save(Persona persona) {
        return data.save(new PersonaEntity(persona)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Persona> findByCodigo(String codigo) {
        return codigo == null ? Optional.empty() : data.findById(codigo.strip()).map(PersonaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Persona> findAll(TipoPersona tipo) {
        List<PersonaEntity> found = tipo == null ? data.findAllByOrderByNombreAsc() : data.findByTipoOrderByNombreAsc(tipo);
        return found.stream().map(PersonaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean delete(String codigo) {
        if (!data.existsById(codigo)) {
            return false;
        }
        data.deleteById(codigo);
        return true;
    }
}
