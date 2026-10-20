package edu.campusnexo.domain.catalog;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

/**
 * A monitored space with the building and branch data needed by the use cases. {@code seatLayout} is
 * {@code null} for rooms without individual seats (offices, storage); {@code computers} counts the
 * workstations whose power state the room's Arduino reports.
 */
public record CampusSpace(
        String spaceId,
        String name,
        SpaceType type,
        int capacity,
        double areaSqm,
        String buildingId,
        String buildingName,
        String branchId,
        String branchName,
        String city,
        ClimateProfile climateProfile,
        double averageOutsideTempC,
        double energyTariffCopPerKwh,
        int floor,
        SeatLayout seatLayout,
        int computers) {

    public CampusSpace {
        requireText(spaceId, "spaceId");
        requireText(name, "name");
        requireNonNull(type, "type");
        requireRange(capacity, 1, 5_000, "capacity");
        requireRange(areaSqm, 0.1, 100_000, "areaSqm");
        requireText(buildingId, "buildingId");
        requireText(branchId, "branchId");
        requireNonNull(climateProfile, "climateProfile");
        requireRange(averageOutsideTempC, -30, 60, "averageOutsideTempC");
        requireRange(energyTariffCopPerKwh, 0, 100_000, "energyTariffCopPerKwh");
        requireRange(floor, -5, 200, "floor");
        requireRange(computers, 0, 1_000, "computers");
    }

    /** Space on the ground floor with no seat grid and no computers. */
    public CampusSpace(String spaceId, String name, SpaceType type, int capacity, double areaSqm, String buildingId,
                       String buildingName, String branchId, String branchName, String city,
                       ClimateProfile climateProfile, double averageOutsideTempC, double energyTariffCopPerKwh) {
        this(spaceId, name, type, capacity, areaSqm, buildingId, buildingName, branchId, branchName, city,
                climateProfile, averageOutsideTempC, energyTariffCopPerKwh, 1, null, 0);
    }
}
