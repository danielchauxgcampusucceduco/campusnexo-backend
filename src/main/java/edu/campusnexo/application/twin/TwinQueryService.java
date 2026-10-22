package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SavingsReport.SpaceSavings;
import edu.campusnexo.application.port.in.SavingsReport;
import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
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
 * reservations, desk regions) instead of two queries per room, and the savings report reads only the twins.
 */
public class TwinQueryService implements TwinQueryUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final SpaceRepositoryPort twins;
    private final ReservationRepository reservations;
    private final SeatRegions seatRegions;
    private final EnergyAccountingPolicy energy;
    private final double emissionFactorKgCo2PerKwh;
    private final RecentTraceBuffer traces;

    public TwinQueryService(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                            ReservationRepository reservations, SeatRegions seatRegions, EnergyAccountingPolicy energy,
                            double emissionFactorKgCo2PerKwh, RecentTraceBuffer traces) {
        this.clock = clock;
        this.catalog = catalog;
        this.twins = twins;
        this.reservations = reservations;
        this.seatRegions = seatRegions;
        this.energy = energy;
        this.emissionFactorKgCo2PerKwh = emissionFactorKgCo2PerKwh;
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

    /** Savings of the rooms that have a climate unit; the others have nothing to save. */
    @Override
    public SavingsReport savings() {
        Instant now = clock.instant();
        Map<String, SpaceTwinState> states = statesById();
        List<SpaceSavings> perSpace = catalog.listSpaces(null).stream()
                .filter(CampusSpace::hvacInstalled)
                .map(space -> {
                    SpaceTwinState state = effective(space, states.get(space.spaceId()), now);
                    double kwh = state.energySavedKwh();
                    return new SpaceSavings(space.spaceId(), space.name(), space.branchName(), state.hvacState(),
                            round(energy.ratedKw(space.areaSqm()), 2), round(kwh, 3),
                            round(kwh * space.energyTariffCopPerKwh(), 0), round(kwh * emissionFactorKgCo2PerKwh, 3));
                }).toList();
        double kwh = perSpace.stream().mapToDouble(SpaceSavings::kwh).sum();
        double cop = perSpace.stream().mapToDouble(SpaceSavings::cop).sum();
        double co2 = perSpace.stream().mapToDouble(SpaceSavings::kgCo2).sum();
        return new SavingsReport(now, round(kwh, 3), round(cop, 0), round(co2, 3), emissionFactorKgCo2PerKwh, perSpace);
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

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
