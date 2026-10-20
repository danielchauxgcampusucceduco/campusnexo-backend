package edu.campusnexo.infrastructure.persistence.occupancy;

import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.port.OccupancyEstimateRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaOccupancyEstimateAdapter implements OccupancyEstimateRepository {
    private final SpringDataOccupancyEstimateRepository data;

    JpaOccupancyEstimateAdapter(SpringDataOccupancyEstimateRepository data) {
        this.data = data;
    }

    @Override
    @Transactional
    public OccupancyRecord save(OccupancyRecord record) {
        return data.save(new OccupancyEstimateEntity(record)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OccupancyRecord> findById(UUID estimateId) {
        return data.findById(estimateId).map(OccupancyEstimateEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccupancyRecord> findRecent(String spaceId, ResultLimit limit) {
        return data.findBySpaceIdOrderByEstimatedAtDesc(spaceId, Limit.of(limit.value())).stream()
                .map(OccupancyEstimateEntity::toDomain).toList();
    }
}
