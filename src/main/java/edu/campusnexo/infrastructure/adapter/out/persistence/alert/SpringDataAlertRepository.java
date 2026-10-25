package edu.campusnexo.infrastructure.adapter.out.persistence.alert;

import edu.campusnexo.domain.alert.AlertType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAlertRepository extends JpaRepository<AlertEntity, UUID> {
    List<AlertEntity> findAllByOrderByDetectedAtDesc(Limit limit);

    /** Keyset page, newest first: alerts strictly older than ({@code at}, {@code tieId}). */
    @Query("""
            select a from AlertEntity a
            where (:allSpaces = true or a.spaceId = :spaceId)
              and a.type in :types
              and (a.detectedAt < :at or (a.detectedAt = :at and a.alertId < :tieId))
            order by a.detectedAt desc, a.alertId desc
            """)
    List<AlertEntity> findPage(@Param("allSpaces") boolean allSpaces, @Param("spaceId") String spaceId,
                               @Param("types") Collection<AlertType> types, @Param("at") Instant at,
                               @Param("tieId") UUID tieId, Limit limit);

    List<AlertEntity> findBySpaceIdOrderByDetectedAtDesc(String spaceId, Limit limit);

    List<AlertEntity> findByTypeOrderByDetectedAtDesc(AlertType type, Limit limit);

    List<AlertEntity> findBySpaceIdAndTypeOrderByDetectedAtDesc(String spaceId, AlertType type, Limit limit);

    boolean existsBySpaceIdAndTypeAndDetectedAtGreaterThanEqual(String spaceId, AlertType type, Instant from);

    @Query("select a.type, count(a) from AlertEntity a where a.detectedAt >= :from group by a.type")
    List<Object[]> countByTypeSince(@Param("from") Instant from);
}
