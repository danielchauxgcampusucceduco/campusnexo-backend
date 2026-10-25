package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.OccupancyStatus;
import edu.campusnexo.application.port.in.SpaceOccupancyStatus;
import edu.campusnexo.domain.catalog.SpaceType;
import java.time.Instant;

public record SpaceOccupancyResponse(
        String spaceId,
        String name,
        SpaceType type,
        String branchName,
        int capacity,
        Integer peopleCount,
        Integer freeSeats,
        Double occupancyPct,
        OccupancyStatus status,
        Instant lastReadingAt,
        boolean stale) {

    public static SpaceOccupancyResponse from(SpaceOccupancyStatus status) {
        return new SpaceOccupancyResponse(status.space().spaceId(), status.space().name(), status.space().type(),
                status.space().branchName(), status.space().capacity(), status.peopleCount(), status.freeSeats(),
                status.occupancyPct(), status.status(), status.lastReadingAt(), status.stale());
    }
}
