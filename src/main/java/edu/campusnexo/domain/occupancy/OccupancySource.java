package edu.campusnexo.domain.occupancy;

/** Where an occupancy value came from, so every decision can be traced back to its input. */
public enum OccupancySource {
    MANUAL,
    SERVICIO_IA,
    RESPALDO_LOCAL
}
