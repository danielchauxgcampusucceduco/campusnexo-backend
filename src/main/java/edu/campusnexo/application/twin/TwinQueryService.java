package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SavingsReport;
import edu.campusnexo.application.port.in.SavingsReport.SpaceSavings;
import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TwinQueryService implements TwinQueryUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final SpaceRepositoryPort twins;
    private final ReservationRepository reservations;
    private final EnergyAccountingPolicy energy;
    private final double emissionFactorKgCo2PerKwh;
    private final RecentTraceBuffer traces;

    public TwinQueryService(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                            ReservationRepository reservations, EnergyAccountingPolicy energy,
                            double emissionFactorKgCo2PerKwh, RecentTraceBuffer traces) {
        this.clock = clock;
        this.catalog = catalog;
        this.twins = twins;
        this.reservations = reservations;
        this.energy = energy;
        this.emissionFactorKgCo2PerKwh = emissionFactorKgCo2PerKwh;
        this.traces = traces;
    }

    @Override
    public List<SpaceTwinView> spaces() {
        Instant now = clock.instant();
        Map<String, SpaceTwinState> states = twins.findAllTwins().stream()
                .collect(Collectors.toMap(SpaceTwinState::spaceId, Function.identity()));
        return catalog.listSpaces(null).stream()
                .map(space -> view(space, states.get(space.spaceId()), now))
                .toList();
    }

    @Override
    public SpaceTwinView space(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        return view(space, twins.findTwin(spaceId).orElse(null), clock.instant());
    }

    @Override
    public SavingsReport savings() {
        List<SpaceSavings> perSpace = spaces().stream().map(view -> {
            CampusSpace space = view.space();
            double kwh = view.state().energySavedKwh();
            return new SpaceSavings(space.spaceId(), space.name(), space.branchName(), view.state().hvacState(),
                    round(energy.ratedKw(space.areaSqm()), 2), round(kwh, 3),
                    round(kwh * space.energyTariffCopPerKwh(), 0), round(kwh * emissionFactorKgCo2PerKwh, 3));
        }).toList();
        double kwh = perSpace.stream().mapToDouble(SpaceSavings::kwh).sum();
        double cop = perSpace.stream().mapToDouble(SpaceSavings::cop).sum();
        double co2 = perSpace.stream().mapToDouble(SpaceSavings::kgCo2).sum();
        return new SavingsReport(clock.instant(), round(kwh, 3), round(cop, 0), round(co2, 3),
                emissionFactorKgCo2PerKwh, perSpace);
    }

    @Override
    public List<ArchitectureTrace> recentTraces(int limit) {
        return traces.latest(ResultLimit.of(limit).value());
    }

    private SpaceTwinView view(CampusSpace space, SpaceTwinState state, Instant now) {
        SpaceTwinState effective = state == null ? SpaceTwinState.initial(space, now) : state;
        return new SpaceTwinView(space, effective, reservations.findActiveAt(space.spaceId(), now).orElse(null),
                twins.findRois(space.spaceId()));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
