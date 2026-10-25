package edu.campusnexo.infrastructure.adapter.out.persistence.catalog;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SeatLayout;
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

    @Column(nullable = false)
    private int floor;

    @Column(name = "seat_rows")
    private Integer seatRows;

    @Column(name = "seat_columns")
    private Integer seatColumns;

    @Column(nullable = false)
    private int computers;

    @Column(name = "hvac_installed", nullable = false)
    private boolean hvacInstalled;

    protected CampusSpaceEntity() {
    }

    /** Only the measurements change; identity, building and type come from the migrations. */
    void ajustar(CampusSpace space) {
        capacity = space.capacity();
        areaSqm = space.areaSqm();
        seatRows = space.seatLayout() == null ? null : space.seatLayout().rows();
        seatColumns = space.seatLayout() == null ? null : space.seatLayout().columns();
        computers = space.computers();
        hvacInstalled = space.hvacInstalled();
    }

    CampusSpace toDomain() {
        CampusBranchEntity branch = building.getBranch();
        return new CampusSpace(spaceId, name, type, capacity, areaSqm, building.getBuildingId(), building.getName(),
                branch.getBranchId(), branch.getName(), branch.getCity(), branch.getClimateProfile(),
                branch.getAverageOutsideTempC(), branch.getKwhTariffCop().doubleValue(), floor,
                seatRows == null || seatColumns == null ? null : new SeatLayout(seatRows, seatColumns), computers,
                hvacInstalled);
    }
}
