package edu.campusnexo.application.occupancy;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.LiveOccupancyUseCase;
import edu.campusnexo.application.port.in.OccupancyStatus;
import edu.campusnexo.application.port.in.SpaceOccupancyStatus;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LiveOccupancyService implements LiveOccupancyUseCase {
    static final double MODERATE_FROM_PCT = 60;
    static final double SATURATED_FROM_PCT = 85;

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TelemetryRepository telemetry;
    private final Duration staleAfter;

    public LiveOccupancyService(Clock clock, CampusCatalogUseCase catalog, TelemetryRepository telemetry,
                                Duration staleAfter) {
        this.clock = clock;
        this.catalog = catalog;
        this.telemetry = telemetry;
        this.staleAfter = staleAfter;
    }

    @Override
    public List<SpaceOccupancyStatus> current(SpaceType type) {
        Map<String, Telemetry> latest = telemetry.findLatestPerSpace().stream()
                .collect(Collectors.toMap(Telemetry::spaceId, Function.identity(), (a, b) -> a));
        Instant now = clock.instant();
        return catalog.listSpaces(type).stream()
                .map(space -> status(space, latest.get(space.spaceId()), now))
                .sorted(Comparator.comparing((SpaceOccupancyStatus s) -> s.freeSeats() == null)
                        .thenComparing(s -> s.freeSeats() == null ? 0 : -s.freeSeats()))
                .toList();
    }

    static OccupancyStatus classify(double occupancyPct) {
        if (occupancyPct >= SATURATED_FROM_PCT) {
            return OccupancyStatus.SATURADO;
        }
        return occupancyPct >= MODERATE_FROM_PCT ? OccupancyStatus.MODERADO : OccupancyStatus.LIBRE;
    }

    private SpaceOccupancyStatus status(CampusSpace space, Telemetry reading, Instant now) {
        if (reading == null) {
            return new SpaceOccupancyStatus(space, null, null, null, OccupancyStatus.SIN_DATOS, null, true);
        }
        boolean stale = reading.recordedAt().isBefore(now.minus(staleAfter));
        if (stale) {
            return new SpaceOccupancyStatus(space, null, null, null, OccupancyStatus.SIN_DATOS, reading.recordedAt(), true);
        }
        int people = reading.peopleCount();
        double pct = Math.round(people * 1000.0 / space.capacity()) / 10.0;
        return new SpaceOccupancyStatus(space, people, Math.max(0, space.capacity() - people), pct,
                classify(pct), reading.recordedAt(), false);
    }
}
