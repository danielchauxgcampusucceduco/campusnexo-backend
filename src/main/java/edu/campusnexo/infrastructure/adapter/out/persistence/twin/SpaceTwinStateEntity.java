package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.SpaceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Latest twin state per space. Status and HVAC state are columns (queryable from SQL); the full state,
 * including the Kalman and thermal traces, is a JSON document.
 */
@Entity
@Table(name = "space_twin_states")
class SpaceTwinStateEntity {
    @Id
    @Column(name = "space_id", length = 32)
    private String spaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SpaceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "hvac_state", nullable = false, length = 32)
    private HvacControlState hvacState;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "document", nullable = false, columnDefinition = "TEXT")
    private String document;

    protected SpaceTwinStateEntity() {
    }

    SpaceTwinStateEntity(String spaceId, SpaceStatus status, HvacControlState hvacState, Instant updatedAt, String document) {
        this.spaceId = spaceId;
        this.status = status;
        this.hvacState = hvacState;
        this.updatedAt = updatedAt;
        this.document = document;
    }

    String getDocument() {
        return document;
    }
}
