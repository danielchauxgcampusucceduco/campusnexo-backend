package edu.campusnexo.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

interface SpringDataTelemetryRepository extends JpaRepository<TelemetryEntity, Long> {
    List<TelemetryEntity> findTop100BySpaceIdOrderByRecordedAtDesc(String spaceId);
    List<TelemetryEntity> findTop100ByOrderByRecordedAtDesc();
    long countByRecordedAtAfter(Instant from);
}
