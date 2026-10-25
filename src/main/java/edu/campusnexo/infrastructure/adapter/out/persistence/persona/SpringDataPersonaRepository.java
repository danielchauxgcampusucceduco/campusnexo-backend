package edu.campusnexo.infrastructure.adapter.out.persistence.persona;

import edu.campusnexo.domain.persona.TipoPersona;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPersonaRepository extends JpaRepository<PersonaEntity, String> {
    List<PersonaEntity> findAllByOrderByNombreAsc();

    List<PersonaEntity> findByTipoOrderByNombreAsc(TipoPersona tipo);
}
