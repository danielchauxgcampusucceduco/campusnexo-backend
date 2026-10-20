package edu.campusnexo.infrastructure.adapter.out.persistence.alert;

import edu.campusnexo.domain.alert.AlertType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAlertRepository extends JpaRepository<AlertEntity, UUID> {
    List<AlertEntity> findAllByOrderByDetectedAtDesc(Limit limit);

    List<AlertEntity> findBySpaceIdOrderByDetectedAtDesc(String spaceId, Limit limit);

    List<AlertEntity> findByTypeOrderByDetectedAtDesc(AlertType type, Limit limit);

    List<AlertEntity> findBySpaceIdAndTypeOrderByDetectedAtDesc(String spaceId, AlertType type, Limit limit);

    boolean existsBySpaceIdAndTypeAndDetectedAtGreaterThanEqual(String spaceId, AlertType type, Instant from);

    @Query("select a.type, count(a) from AlertEntity a where a.detectedAt >= :from group by a.type")
    List<Object[]> countByTypeSince(@Param("from") Instant from);
}
