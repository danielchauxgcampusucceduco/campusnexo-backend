package edu.campusnexo.infrastructure.adapter.out.persistence.activo;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataActivoRepository extends JpaRepository<ActivoEntity, String> {
    List<ActivoEntity> findBySpaceIdOrderByNumeroPuestoAscCodigoAsc(String spaceId);

    List<ActivoEntity> findBySpaceIdAndNumeroPuestoOrderByCodigoAsc(String spaceId, int numeroPuesto);

    /** Only moves the heartbeat forward, so a late one never makes the computer look off. */
    @Modifying
    @Query("""
            update ActivoEntity a set a.ultimoLatido = :at
            where a.codigo = :codigo and (a.ultimoLatido is null or a.ultimoLatido < :at)
            """)
    int saveLatido(@Param("codigo") String codigo, @Param("at") Instant at);
}
