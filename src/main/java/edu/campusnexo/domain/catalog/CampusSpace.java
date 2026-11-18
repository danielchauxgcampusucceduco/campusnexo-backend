package edu.campusnexo.domain.catalog;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

/**
 * A space of the campus with the building and branch data needed by the use cases. {@code seatLayout} is
 * {@code null} for rooms without individual seats (offices, storage); {@code computers} counts the workstations of the
 * room (their agents report whether each one is on). Only the spaces a student can use are followed by the camera and
 * offered as available ({@link SpaceType#accesibleParaEstudiantes()}).
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
        requireRange(floor, -5, 200, "floor");
        requireRange(computers, 0, 1_000, "computers");
    }

    /** Space on the ground floor with no seat grid and no computers. */
    public CampusSpace(String spaceId, String name, SpaceType type, int capacity, double areaSqm, String buildingId,
                       String buildingName, String branchId, String branchName, String city) {
        this(spaceId, name, type, capacity, areaSqm, buildingId, buildingName, branchId, branchName, city, 1, null, 0);
    }

    /** Whether a student can use the space: the camera follows it and it is offered as available. */
    public boolean accesibleParaEstudiantes() {
        return type.accesibleParaEstudiantes();
    }

    /** The same space with the measurements taken on site; identity, building and type do not change. */
    public CampusSpace conMedidas(int capacity, double areaSqm, SeatLayout seatLayout, int computers) {
        return new CampusSpace(spaceId, name, type, capacity, areaSqm, buildingId, buildingName, branchId, branchName,
                city, floor, seatLayout, computers);
    }
}
