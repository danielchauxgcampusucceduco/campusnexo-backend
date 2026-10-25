package edu.campusnexo.support;

import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.port.OccupancyEstimateRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryOccupancyEstimateRepository implements OccupancyEstimateRepository {
    private final List<OccupancyRecord> rows = new ArrayList<>();

    public List<OccupancyRecord> rows() {
        return rows;
    }

    @Override
    public OccupancyRecord save(OccupancyRecord record) {
        rows.add(record);
        return record;
    }

    @Override
    public Optional<OccupancyRecord> findById(UUID estimateId) {
        return rows.stream().filter(row -> row.estimateId().equals(estimateId)).findFirst();
    }

    @Override
    public List<OccupancyRecord> findRecent(String spaceId, ResultLimit limit) {
        return rows.stream().filter(row -> row.estimate().spaceId().equals(spaceId))
                .sorted(Comparator.comparing(OccupancyRecord::estimatedAt).reversed()).limit(limit.value()).toList();
    }
}
