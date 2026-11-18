package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the twin. The campus view is built with one query per kind of data (twins, active
 * reservations, desk regions) instead of two queries per room.
 */
public class TwinQueryService implements TwinQueryUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final SpaceRepositoryPort twins;
    private final ReservationRepository reservations;
    private final SeatRegions seatRegions;
    private final RecentTraceBuffer traces;

    public TwinQueryService(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                            ReservationRepository reservations, SeatRegions seatRegions, RecentTraceBuffer traces) {
        this.clock = clock;
        this.catalog = catalog;
        this.twins = twins;
        this.reservations = reservations;
        this.seatRegions = seatRegions;
        this.traces = traces;
    }

    @Override
    public List<SpaceTwinView> spaces() {
        Instant now = clock.instant();
        List<CampusSpace> spaces = catalog.listSpaces(null);
        Map<String, SpaceTwinState> states = statesById();
        Map<String, Reservation> active = reservations.findActiveAtForAll(now);
        Map<String, List<StudyZoneRoi>> regions = seatRegions.ofAll(spaces);
        return spaces.stream()
                .map(space -> new SpaceTwinView(space, effective(space, states.get(space.spaceId()), now),
                        active.get(space.spaceId()), regions.get(space.spaceId())))
                .toList();
    }

    @Override
    public SpaceTwinView space(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        return new SpaceTwinView(space, effective(space, twins.findTwin(spaceId).orElse(null), now),
                reservations.findActiveAt(spaceId, now).orElse(null), seatRegions.of(space));
    }

    @Override
    public List<ArchitectureTrace> recentTraces(int limit) {
        return traces.latest(ResultLimit.of(limit).value());
    }

    private Map<String, SpaceTwinState> statesById() {
        return twins.findAllTwins().stream().collect(Collectors.toMap(SpaceTwinState::spaceId, Function.identity()));
    }

    private static SpaceTwinState effective(CampusSpace space, SpaceTwinState state, Instant now) {
        return state == null ? SpaceTwinState.initial(space, now) : state;
    }
}
