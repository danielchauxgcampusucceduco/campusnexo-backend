package edu.campusnexo.domain.catalog;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

/**
 * A monitored space with the building and branch data needed by the use cases. {@code seatLayout} is
 * {@code null} for rooms without individual seats (offices, storage); {@code computers} counts the
 * workstations whose power state the room's Arduino reports. {@code hvacInstalled} says whether the room has
 * a climate unit the twin can command (rooms without one are never climatized and save no energy).
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
        int computers,
        boolean hvacInstalled) {

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

    /** Space with a climate unit. */
    public CampusSpace(String spaceId, String name, SpaceType type, int capacity, double areaSqm, String buildingId,
                       String buildingName, String branchId, String branchName, String city,
                       ClimateProfile climateProfile, double averageOutsideTempC, double energyTariffCopPerKwh, int floor,
                       SeatLayout seatLayout, int computers) {
        this(spaceId, name, type, capacity, areaSqm, buildingId, buildingName, branchId, branchName, city,
                climateProfile, averageOutsideTempC, energyTariffCopPerKwh, floor, seatLayout, computers, true);
    }

    /** Space on the ground floor with no seat grid and no computers. */
    public CampusSpace(String spaceId, String name, SpaceType type, int capacity, double areaSqm, String buildingId,
                       String buildingName, String branchId, String branchName, String city,
                       ClimateProfile climateProfile, double averageOutsideTempC, double energyTariffCopPerKwh) {
        this(spaceId, name, type, capacity, areaSqm, buildingId, buildingName, branchId, branchName, city,
                climateProfile, averageOutsideTempC, energyTariffCopPerKwh, 1, null, 0, true);
    }

    /** The same space with the measurements taken on site; identity, building and type do not change. */
    public CampusSpace conMedidas(int capacity, double areaSqm, SeatLayout seatLayout, int computers,
                                  boolean hvacInstalled) {
        return new CampusSpace(spaceId, name, type, capacity, areaSqm, buildingId, buildingName, branchId, branchName,
                city, climateProfile, averageOutsideTempC, energyTariffCopPerKwh, floor, seatLayout, computers,
                hvacInstalled);
    }

    public CampusSpace withHvacInstalled(boolean installed) {
        return new CampusSpace(spaceId, name, type, capacity, areaSqm, buildingId, buildingName, branchId, branchName,
                city, climateProfile, averageOutsideTempC, energyTariffCopPerKwh, floor, seatLayout, computers,
                installed);
    }
}
