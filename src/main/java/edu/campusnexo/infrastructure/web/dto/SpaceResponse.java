package edu.campusnexo.infrastructure.web.dto;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.catalog.SpaceType;

public record SpaceResponse(
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

    public static SpaceResponse from(CampusSpace space) {
        return new SpaceResponse(space.spaceId(), space.name(), space.type(), space.capacity(), space.areaSqm(),
                space.buildingId(), space.buildingName(), space.branchId(), space.branchName(), space.city(),
                space.climateProfile(), space.averageOutsideTempC(), space.energyTariffCopPerKwh());
    }
}
