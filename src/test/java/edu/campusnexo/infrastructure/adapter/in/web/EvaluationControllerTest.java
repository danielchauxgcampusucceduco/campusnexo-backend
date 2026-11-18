package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.SpaceEvaluation;
import edu.campusnexo.application.port.in.SpaceEvaluationUseCase;
import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.support.TestData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationControllerTest {
    @Test
    void returnsTheUnifiedSpaceTelemetryAndOccupancyEvaluation() {
        SpaceEvaluationUseCase useCase = mock(SpaceEvaluationUseCase.class);
        Instant at = Instant.parse("2026-10-21T12:00:00Z");
        var space = TestData.andeanClassroom();
        var telemetry = new Telemetry(space.spaceId(), at, 18, 2, 16, null);
        var occupancy = new OccupancyRecord(UUID.randomUUID(), at,
                new OccupancyEstimate(space.spaceId(), 18, 85, true, true, OccupancySource.SERVICIO_IA, "estimate"));
        when(useCase.evaluate(space.spaceId())).thenReturn(
                new SpaceEvaluation(space, telemetry, occupancy, null, List.of()));

        var response = new EvaluationController(useCase).evaluate(space.spaceId());

        assertEquals(space.spaceId(), response.space().spaceId());
        assertEquals(at, response.telemetry().recordedAt());
        assertEquals(18, response.occupancy().occupancyEstimate());
        assertEquals(null, response.activeReservation());
        assertEquals(0, response.recentAlerts().size());
    }
}
