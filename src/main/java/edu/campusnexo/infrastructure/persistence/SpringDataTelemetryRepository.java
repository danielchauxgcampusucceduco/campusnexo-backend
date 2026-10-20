package edu.campusnexo.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

interface SpringDataTelemetryRepository extends JpaRepository<TelemetryEntity, Long> {
    List<TelemetryEntity> findTop100BySpaceIdOrderByRecordedAtDesc(String spaceId);
    List<TelemetryEntity> findTop100ByOrderByRecordedAtDesc();
    long countByRecordedAtGreaterThanEqual(Instant from);

    @Query("select count(distinct telemetry.spaceId) from TelemetryEntity telemetry where telemetry.recordedAt >= :from")
    long countDistinctSpaceIdsSince(@Param("from") Instant from);
}
