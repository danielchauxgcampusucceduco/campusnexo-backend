package edu.campusnexo.application;

import edu.campusnexo.application.port.in.DashboardQueryUseCase;
import edu.campusnexo.domain.port.TelemetryRepository;

import java.time.Clock;
import java.time.Duration;

public class DashboardSummaryService implements DashboardQueryUseCase {
    private static final Duration SUMMARY_WINDOW = Duration.ofHours(24);

    private final Clock clock;
    private final TelemetryRepository telemetry;

    public DashboardSummaryService(Clock clock, TelemetryRepository telemetry) {
        this.clock = clock;
        this.telemetry = telemetry;
    }

    @Override
    public DashboardSummary summarizeLast24Hours() {
        var since = clock.instant().minus(SUMMARY_WINDOW);
        return new DashboardSummary(telemetry.countDistinctSpacesSince(since), telemetry.countSince(since), since);
    }
}
