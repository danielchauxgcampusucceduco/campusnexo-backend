package edu.campusnexo.domain.model;

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
        double averageOutsideTempC) {
    public CampusSpace {
        if (spaceId == null || spaceId.isBlank()) throw new IllegalArgumentException("spaceId es obligatorio");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("El nombre del espacio es obligatorio");
        if (type == null || climateProfile == null) throw new IllegalArgumentException("Tipo y perfil climático son obligatorios");
        if (capacity <= 0 || !Double.isFinite(areaSqm) || areaSqm <= 0 || !Double.isFinite(averageOutsideTempC)) {
            throw new IllegalArgumentException("Capacidad, área o temperatura exterior no válidas");
        }
    }
}
