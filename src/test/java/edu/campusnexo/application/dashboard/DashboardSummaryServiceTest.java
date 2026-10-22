package edu.campusnexo.application.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.LiveOccupancyUseCase;
import edu.campusnexo.application.port.in.OccupancyStatus;
import edu.campusnexo.application.port.in.SpaceOccupancyStatus;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.AlertRepository;
import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.support.TestData;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardSummaryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void summarizesTheSameRollingTwentyFourHourWindowAcrossPorts() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        TelemetryRepository telemetry = mock(TelemetryRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        CampusCatalogUseCase catalog = mock(CampusCatalogUseCase.class);
        LiveOccupancyUseCase occupancy = mock(LiveOccupancyUseCase.class);
        when(alerts.countByTypeSince(NOW.minusSeconds(86_400))).thenReturn(Map.of(AlertType.CO2_ALTO, 2L));
        when(catalog.listSpaces(null)).thenReturn(List.of(TestData.andeanClassroom()));
        when(occupancy.current(null)).thenReturn(List.of(
                new SpaceOccupancyStatus(TestData.andeanClassroom(), 30, 2, 93.8,
                        OccupancyStatus.SATURADO, NOW, false),
                new SpaceOccupancyStatus(TestData.library(), null, null, null,
                        OccupancyStatus.SIN_DATOS, null, true)));
        when(telemetry.findSince(NOW.minusSeconds(86_400), 50_000)).thenReturn(List.of());
        when(telemetry.countDistinctSpacesSince(NOW.minusSeconds(86_400))).thenReturn(3L);
        when(telemetry.countSince(NOW.minusSeconds(86_400))).thenReturn(47L);

        var summary = new DashboardSummaryService(clock, telemetry, alerts, catalog, occupancy,
                new EmptySpaceEnergyEstimator(java.time.Duration.ofMinutes(15))).summarizeLast24Hours();

        assertEquals(NOW.minusSeconds(86_400), summary.since());
        assertEquals(3, summary.monitoredSpaces());
        assertEquals(47, summary.readingsLast24h());
        assertEquals(2, summary.alertsLast24h());
        assertEquals(2L, summary.alertsByType().get(AlertType.CO2_ALTO));
        assertEquals(93.8, summary.averageOccupancyPct());
        assertEquals(1, summary.saturatedSpaces());
        verify(telemetry).findSince(NOW.minusSeconds(86_400), 50_000);
    }
}
