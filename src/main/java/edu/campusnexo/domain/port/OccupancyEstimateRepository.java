package edu.campusnexo.domain.port;

import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.shared.ResultLimit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OccupancyEstimateRepository {
    OccupancyRecord save(OccupancyRecord record);

    Optional<OccupancyRecord> findById(UUID estimateId);

    List<OccupancyRecord> findRecent(String spaceId, ResultLimit limit);
}
