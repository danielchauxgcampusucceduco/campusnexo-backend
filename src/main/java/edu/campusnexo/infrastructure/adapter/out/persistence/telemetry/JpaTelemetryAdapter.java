package edu.campusnexo.infrastructure.persistence.telemetry;

import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaTelemetryAdapter implements TelemetryRepository {
    private final SpringDataTelemetryRepository data;

    JpaTelemetryAdapter(SpringDataTelemetryRepository data) {
        this.data = data;
    }

    @Override
    @Transactional
    public Telemetry save(Telemetry telemetry) {
        return data.save(new TelemetryEntity(telemetry)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Telemetry> findRecent(String spaceId, ResultLimit limit) {
        Limit rows = Limit.of(limit.value());
        List<TelemetryEntity> entities = spaceId == null
                ? data.findAllByOrderByRecordedAtDesc(rows)
                : data.findBySpaceIdOrderByRecordedAtDesc(spaceId, rows);
        return entities.stream().map(TelemetryEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Telemetry> findLatest(String spaceId) {
        return data.findFirstBySpaceIdOrderByRecordedAtDesc(spaceId).map(TelemetryEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Telemetry> findLatestPerSpace() {
        return data.findLatestPerSpace().stream().map(TelemetryEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Telemetry> findBySpaceAndRecordedAt(String spaceId, Instant recordedAt) {
        return data.findFirstBySpaceIdAndRecordedAt(spaceId, recordedAt).map(TelemetryEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Telemetry> findSince(Instant from, int maxRows) {
        return data.findByRecordedAtGreaterThanEqualOrderBySpaceIdAscRecordedAtAsc(from, Limit.of(maxRows)).stream()
                .map(TelemetryEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countSince(Instant from) {
        return data.countByRecordedAtGreaterThanEqual(from);
    }

    @Override
    @Transactional(readOnly = true)
    public long countDistinctSpacesSince(Instant from) {
        return data.countDistinctSpaceIdsSince(from);
    }
}
