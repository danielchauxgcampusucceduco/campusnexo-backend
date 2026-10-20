package edu.campusnexo.domain.model;

public record OccupancyEstimate(String spaceId, int occupancyEstimate, double confidencePct,
                                boolean cameraAvailable, boolean isEstimate, String note) {}
