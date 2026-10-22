package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.DashboardQueryUseCase;
import edu.campusnexo.application.port.in.DashboardSummary;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardControllerTest {
    @Test
    void mapsTheDashboardUseCaseSummaryToTheReadOnlyResponse() {
        DashboardQueryUseCase query = mock(DashboardQueryUseCase.class);
        DashboardSummary summary = new DashboardSummary(Instant.EPOCH, 4, 120, 3,
                Map.of(), 42.5, 1, 2.4, 35_000);
        when(query.summarizeLast24Hours()).thenReturn(summary);

        var response = new DashboardController(query).summary();

        assertEquals(4, response.monitoredSpaces());
        assertEquals(120, response.readingsLast24h());
        assertEquals(42.5, response.averageOccupancyPct());
        assertEquals(2.4, response.estimatedEmptySpaceEnergyKwh());
    }
}
