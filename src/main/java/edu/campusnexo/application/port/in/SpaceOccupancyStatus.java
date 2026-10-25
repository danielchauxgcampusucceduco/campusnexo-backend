package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.catalog.CampusSpace;
import java.time.Instant;

/** Live occupancy of a space; reading-derived fields are {@code null} when there is no fresh data. */
public record SpaceOccupancyStatus(
        CampusSpace space,
        Integer peopleCount,
        Integer freeSeats,
        Double occupancyPct,
        OccupancyStatus status,
        Instant lastReadingAt,
        boolean stale) {
}
