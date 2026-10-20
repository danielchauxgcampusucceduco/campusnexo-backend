package edu.campusnexo.infrastructure.persistence;

import edu.campusnexo.domain.model.Telemetry;
import edu.campusnexo.domain.port.TelemetryRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
class JpaTelemetryAdapter implements TelemetryRepository {
    private final SpringDataTelemetryRepository data;
    JpaTelemetryAdapter(SpringDataTelemetryRepository data) { this.data = data; }
    @Override public Telemetry save(Telemetry telemetry) { return data.save(new TelemetryEntity(telemetry)).toDomain(); }
    @Override public List<Telemetry> findRecent(String spaceId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit debe estar entre 1 y 100");
        List<TelemetryEntity> rows = spaceId == null || spaceId.isBlank()
                ? data.findTop100ByOrderByRecordedAtDesc() : data.findTop100BySpaceIdOrderByRecordedAtDesc(spaceId);
        return rows.stream().limit(limit).map(TelemetryEntity::toDomain).toList();
    }
    @Override public long countSince(Instant from) { return data.countByRecordedAtGreaterThanEqual(from); }
    @Override public long countDistinctSpacesSince(Instant from) { return data.countDistinctSpaceIdsSince(from); }
}
