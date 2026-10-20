package edu.campusnexo.infrastructure.adapter.out.persistence.occupancy;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.occupancy.OccupancySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "occupancy_estimates")
class OccupancyEstimateEntity {
    @Id
    @Column(name = "estimate_id", nullable = false)
    private UUID estimateId;

    @Column(name = "space_id", nullable = false, length = 32)
    private String spaceId;

    @Column(name = "estimated_at", nullable = false)
    private Instant estimatedAt;

    @Column(name = "occupancy_estimate", nullable = false)
    private int occupancyEstimate;

    @Column(name = "confidence_pct", nullable = false)
    private double confidencePct;

    @Column(name = "camera_available", nullable = false)
    private boolean cameraAvailable;

    @Column(name = "is_estimate", nullable = false)
    private boolean estimate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 24)
    private OccupancySource source;

    @Column(name = "note", nullable = false, columnDefinition = "TEXT")
    private String note;

    protected OccupancyEstimateEntity() {
    }

    OccupancyEstimateEntity(OccupancyRecord record) {
        OccupancyEstimate value = record.estimate();
        estimateId = record.estimateId();
        spaceId = value.spaceId();
        estimatedAt = record.estimatedAt();
        occupancyEstimate = value.occupancyEstimate();
        confidencePct = value.confidencePct();
        cameraAvailable = value.cameraAvailable();
        estimate = value.isEstimate();
        source = value.source();
        note = value.note();
    }

    OccupancyRecord toDomain() {
        return new OccupancyRecord(estimateId, estimatedAt, new OccupancyEstimate(spaceId, occupancyEstimate,
                confidencePct, cameraAvailable, estimate, source, note));
    }
}
