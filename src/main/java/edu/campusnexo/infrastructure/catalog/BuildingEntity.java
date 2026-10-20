package edu.campusnexo.infrastructure.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "buildings")
class BuildingEntity {
    @Id @Column(name = "building_id", length = 32) private String buildingId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false) private CampusBranchEntity branch;
    @Column(nullable = false, length = 120) private String name;
    protected BuildingEntity() {}
    String getBuildingId() { return buildingId; }
    String getName() { return name; }
    CampusBranchEntity getBranch() { return branch; }
}
