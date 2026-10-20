package edu.campusnexo.domain.catalog;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

/** A monitored space with the building and branch data needed by the use cases. */
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
        double energyTariffCopPerKwh) {

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
    }
}
