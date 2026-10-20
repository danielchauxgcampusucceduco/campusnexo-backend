package edu.campusnexo.application.dashboard;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.DashboardQueryUseCase;
import edu.campusnexo.application.port.in.DashboardSummary;
import edu.campusnexo.application.port.in.LiveOccupancyUseCase;
import edu.campusnexo.application.port.in.OccupancyStatus;
import edu.campusnexo.application.port.in.SpaceOccupancyStatus;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.AlertRepository;
import edu.campusnexo.domain.port.TelemetryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardSummaryService implements DashboardQueryUseCase {
    static final Duration WINDOW = Duration.ofHours(24);
    static final int MAX_READINGS = 50_000;

    private final Clock clock;
    private final TelemetryRepository telemetry;
    private final AlertRepository alerts;
    private final CampusCatalogUseCase catalog;
    private final LiveOccupancyUseCase liveOccupancy;
    private final EmptySpaceEnergyEstimator energyEstimator;

    public DashboardSummaryService(Clock clock, TelemetryRepository telemetry, AlertRepository alerts,
                                   CampusCatalogUseCase catalog, LiveOccupancyUseCase liveOccupancy,
                                   EmptySpaceEnergyEstimator energyEstimator) {
        this.clock = clock;
        this.telemetry = telemetry;
        this.alerts = alerts;
        this.catalog = catalog;
        this.liveOccupancy = liveOccupancy;
        this.energyEstimator = energyEstimator;
    }

    @Override
    public DashboardSummary summarizeLast24Hours() {
        Instant now = clock.instant();
        Instant since = now.minus(WINDOW);

        Map<AlertType, Long> alertsByType = new EnumMap<>(AlertType.class);
        for (AlertType type : AlertType.values()) {
            alertsByType.put(type, 0L);
        }
        alertsByType.putAll(alerts.countByTypeSince(since));
        long totalAlerts = alertsByType.values().stream().mapToLong(Long::longValue).sum();

        List<SpaceOccupancyStatus> live = liveOccupancy.current(null);
        Double averageOccupancy = live.stream().filter(s -> s.occupancyPct() != null)
                .mapToDouble(SpaceOccupancyStatus::occupancyPct).average().stream()
                .map(value -> Math.round(value * 10) / 10.0).boxed().findFirst().orElse(null);
        int saturated = (int) live.stream().filter(s -> s.status() == OccupancyStatus.SATURADO).count();

        Map<String, Double> tariffs = catalog.listSpaces(null).stream()
                .collect(Collectors.toMap(CampusSpace::spaceId, CampusSpace::energyTariffCopPerKwh));
        EmptySpaceEnergyEstimator.Result energy = energyEstimator.estimate(
                telemetry.findSince(since, MAX_READINGS), now, EmptySpaceEnergyEstimator.tariffLookup(tariffs));

        return new DashboardSummary(since, telemetry.countDistinctSpacesSince(since), telemetry.countSince(since),
                totalAlerts, Map.copyOf(alertsByType), averageOccupancy, saturated, energy.kwh(), energy.costCop());
    }
}
