package edu.campusnexo.infrastructure.adapter.out.persistence.activo;

import edu.campusnexo.infrastructure.adapter.out.persistence.activo.RegistroEntities.AgenteEntity;
import edu.campusnexo.infrastructure.adapter.out.persistence.activo.RegistroEntities.EmparejamientoEntity;
import edu.campusnexo.infrastructure.adapter.out.persistence.activo.RegistroEntities.PropuestaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPropuestaRepository extends JpaRepository<PropuestaEntity, String> {
}

interface SpringDataEmparejamientoRepository extends JpaRepository<EmparejamientoEntity, String> {
}

interface SpringDataAgenteRepository extends JpaRepository<AgenteEntity, String> {
    List<AgenteEntity> findBySpaceIdOrderByCpuAsc(String spaceId);
}
