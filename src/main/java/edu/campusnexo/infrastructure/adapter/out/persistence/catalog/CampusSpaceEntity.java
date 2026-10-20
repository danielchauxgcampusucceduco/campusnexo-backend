package edu.campusnexo.infrastructure.adapter.out.persistence.catalog;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "spaces")
class CampusSpaceEntity {
    @Id
    @Column(name = "space_id", length = 32)
    private String spaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private BuildingEntity building;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 24)
    private SpaceType type;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "area_sqm", nullable = false)
    private double areaSqm;

    protected CampusSpaceEntity() {
    }

    CampusSpace toDomain() {
        CampusBranchEntity branch = building.getBranch();
        return new CampusSpace(spaceId, name, type, capacity, areaSqm, building.getBuildingId(), building.getName(),
                branch.getBranchId(), branch.getName(), branch.getCity(), branch.getClimateProfile(),
                branch.getAverageOutsideTempC(), branch.getKwhTariffCop().doubleValue());
    }
}
