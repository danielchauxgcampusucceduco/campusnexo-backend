package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.ClimateRecommendationUseCase;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.LightingDecision;
import edu.campusnexo.domain.climate.VentilationDecision;
import edu.campusnexo.domain.climate.VentilationMode;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ClimateRecommendationRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ClimateControllerTest {
    @Test
    void mapsRecommendationAndPublishesItsResourceLocation() {
        ClimateRecommendationUseCase useCase = mock(ClimateRecommendationUseCase.class);
        UUID id = UUID.fromString("85b84f8a-76ef-4fd2-9d69-ae1a1c264e22");
        ClimateRecommendation recommendation = ClimateRecommendation.builder()
                .recommendationId(id).spaceId("A-301").climateProfile(ClimateProfile.SEDE_ANDINA)
                .generatedAt(Instant.EPOCH)
                .hvac(new HvacDecision(HvacMode.CALEFACCION, 21.0, "confort"))
                .ventilation(new VentilationDecision(VentilationMode.NATURAL, "CO2"))
                .lighting(new LightingDecision(60, false, "luz natural"))
                .occupancySource(OccupancySource.MANUAL).build();
        when(useCase.recommend(any())).thenReturn(recommendation);
        ClimateRecommendationRequest request = new ClimateRecommendationRequest(10, 650.0, 20.0,
                14.0, 50.0, 300.0, null, null);

        var response = new ClimateController(useCase).recommend("A-301", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/v1/climate/recommendations/" + id, response.getHeaders().getLocation().getPath());
        assertEquals(HvacMode.CALEFACCION, response.getBody().hvac().mode());
        assertEquals(OccupancySource.MANUAL, response.getBody().occupancySource());
    }
}
