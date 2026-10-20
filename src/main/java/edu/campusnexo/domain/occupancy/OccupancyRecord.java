package edu.campusnexo.domain.occupancy;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;

import java.time.Instant;
import java.util.UUID;

/** A persisted estimate, so AI results are auditable like climate recommendations. */
public record OccupancyRecord(UUID estimateId, Instant estimatedAt, OccupancyEstimate estimate) {
    public OccupancyRecord {
        requireNonNull(estimateId, "estimateId");
        requireNonNull(estimatedAt, "estimatedAt");
        requireNonNull(estimate, "estimate");
    }
}
