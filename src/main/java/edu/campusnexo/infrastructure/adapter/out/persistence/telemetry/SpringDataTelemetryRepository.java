package edu.campusnexo.infrastructure.adapter.out.persistence.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTelemetryRepository extends JpaRepository<TelemetryEntity, Long> {
    List<TelemetryEntity> findBySpaceIdOrderByRecordedAtDesc(String spaceId, Limit limit);

    List<TelemetryEntity> findAllByOrderByRecordedAtDesc(Limit limit);

    Optional<TelemetryEntity> findFirstBySpaceIdOrderByRecordedAtDesc(String spaceId);

    Optional<TelemetryEntity> findFirstBySpaceIdAndRecordedAt(String spaceId, Instant recordedAt);

    List<TelemetryEntity> findByRecordedAtGreaterThanEqualOrderBySpaceIdAscRecordedAtAsc(Instant from, Limit limit);

    long countByRecordedAtGreaterThanEqual(Instant from);

    @Query("select count(distinct t.spaceId) from TelemetryEntity t where t.recordedAt >= :from")
    long countDistinctSpaceIdsSince(@Param("from") Instant from);

    @Query("""
            select t from TelemetryEntity t
            where t.recordedAt = (select max(t2.recordedAt) from TelemetryEntity t2 where t2.spaceId = t.spaceId)
            """)
    List<TelemetryEntity> findLatestPerSpace();
}
