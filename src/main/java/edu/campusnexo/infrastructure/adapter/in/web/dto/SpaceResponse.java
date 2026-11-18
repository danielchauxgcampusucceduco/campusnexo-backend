package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.domain.catalog.CampusSpace;
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
        int floor,
        Integer seatRows,
        Integer seatColumns,
        int computers,
        boolean accesibleParaEstudiantes) {

    public static SpaceResponse from(CampusSpace space) {
        return new SpaceResponse(space.spaceId(), space.name(), space.type(), space.capacity(), space.areaSqm(),
                space.buildingId(), space.buildingName(), space.branchId(), space.branchName(), space.city(),
                space.floor(), space.seatLayout() == null ? null : space.seatLayout().rows(),
                space.seatLayout() == null ? null : space.seatLayout().columns(), space.computers(),
                space.accesibleParaEstudiantes());
    }
}
