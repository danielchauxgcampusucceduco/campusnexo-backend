package edu.campusnexo.domain.model;

public record OccupancyEstimate(String spaceId, int occupancyEstimate, double confidencePct,
                                boolean cameraAvailable, boolean isEstimate, String note) {
    public OccupancyEstimate {
        if (spaceId == null || spaceId.isBlank()) throw new IllegalArgumentException("spaceId es obligatorio");
        if (occupancyEstimate < 0) throw new IllegalArgumentException("La ocupación estimada no puede ser negativa");
        if (!Double.isFinite(confidencePct) || confidencePct < 0 || confidencePct > 100) {
            throw new IllegalArgumentException("La confianza debe estar entre 0 y 100 %");
        }
        if (note == null) note = "";
    }
}
