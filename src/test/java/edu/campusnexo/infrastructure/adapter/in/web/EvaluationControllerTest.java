package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.SpaceEvaluation;
import edu.campusnexo.application.port.in.SpaceEvaluationUseCase;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.LightingDecision;
import edu.campusnexo.domain.climate.VentilationDecision;
import edu.campusnexo.domain.climate.VentilationMode;
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
    void returnsTheUnifiedSpaceTelemetryOccupancyAndClimateEvaluation() {
        SpaceEvaluationUseCase useCase = mock(SpaceEvaluationUseCase.class);
        Instant at = Instant.parse("2026-10-21T12:00:00Z");
        var space = TestData.andeanClassroom();
        var telemetry = new Telemetry(space.spaceId(), at, 18, 2, 16, 700, 22, 50, 300, 1.5);
        var occupancy = new OccupancyRecord(UUID.randomUUID(), at,
                new OccupancyEstimate(space.spaceId(), 18, 85, true, true, OccupancySource.SERVICIO_IA, "estimate"));
        var recommendation = ClimateRecommendation.builder().spaceId(space.spaceId())
                .climateProfile(ClimateProfile.SEDE_ANDINA).generatedAt(at)
                .hvac(new HvacDecision(HvacMode.CALEFACCION, 21, "confort"))
                .ventilation(new VentilationDecision(VentilationMode.NINGUNA, "CO2 normal"))
                .lighting(new LightingDecision(60, false, "luz"))
                .occupancySource(OccupancySource.SERVICIO_IA).build();
        when(useCase.evaluate(space.spaceId())).thenReturn(
                new SpaceEvaluation(space, telemetry, occupancy, recommendation, null, List.of()));

        var response = new EvaluationController(useCase).evaluate(space.spaceId());

        assertEquals(space.spaceId(), response.space().spaceId());
        assertEquals(at, response.telemetry().recordedAt());
        assertEquals(18, response.occupancy().occupancyEstimate());
        assertEquals(HvacMode.CALEFACCION, response.recommendation().hvac().mode());
        assertEquals(null, response.activeReservation());
        assertEquals(0, response.recentAlerts().size());
    }
}
