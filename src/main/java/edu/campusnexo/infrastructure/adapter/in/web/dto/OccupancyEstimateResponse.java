package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.occupancy.OccupancySource;
import java.time.Instant;
import java.util.UUID;

/** {@code occupancyEstimate} and {@code confidencePct} are read by the React dashboard. */
public record OccupancyEstimateResponse(
        UUID estimateId,
        String spaceId,
        Instant estimatedAt,
        int occupancyEstimate,
        double confidencePct,
        boolean cameraAvailable,
        boolean isEstimate,
        OccupancySource source,
        String note) {

    public static OccupancyEstimateResponse from(OccupancyRecord record) {
        OccupancyEstimate value = record.estimate();
        return new OccupancyEstimateResponse(record.estimateId(), value.spaceId(), record.estimatedAt(),
                value.occupancyEstimate(), value.confidencePct(), value.cameraAvailable(), value.isEstimate(),
                value.source(), value.note());
    }
}
