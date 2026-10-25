package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.occupancy.OccupancyRecord;
import java.util.List;
import java.util.UUID;

public interface OccupancyEstimationUseCase {
    OccupancyRecord estimate(EstimateOccupancyCommand command);

    OccupancyRecord get(UUID estimateId);

    List<OccupancyRecord> recent(String spaceId, int limit);
}
