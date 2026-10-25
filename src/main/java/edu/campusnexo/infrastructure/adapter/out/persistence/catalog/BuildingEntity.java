package edu.campusnexo.infrastructure.adapter.out.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "buildings")
class BuildingEntity {
    @Id
    @Column(name = "building_id", length = 32)
    private String buildingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private CampusBranchEntity branch;

    @Column(nullable = false, length = 120)
    private String name;

    protected BuildingEntity() {
    }

    String getBuildingId() {
        return buildingId;
    }

    String getName() {
        return name;
    }

    CampusBranchEntity getBranch() {
        return branch;
    }
}
