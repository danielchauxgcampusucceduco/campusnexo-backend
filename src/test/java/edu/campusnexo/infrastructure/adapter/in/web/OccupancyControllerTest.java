package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.LiveOccupancyUseCase;
import edu.campusnexo.application.port.in.OccupancyEstimationUseCase;
import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.infrastructure.adapter.in.web.dto.OccupancyEstimateRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OccupancyControllerTest {
    @Test
    void mapsAnEstimateToAResourceLocationAndRetainsItsSourceMetadata() {
        OccupancyEstimationUseCase estimates = mock(OccupancyEstimationUseCase.class);
        LiveOccupancyUseCase live = mock(LiveOccupancyUseCase.class);
        UUID id = UUID.fromString("37d0a401-0ecf-48e2-8a2d-c102c35b0d08");
        OccupancyEstimate result = new OccupancyEstimate("A-301", 18, 35, false, true,
                OccupancySource.SERVICIO_IA, "estimación no calibrada");
        when(estimates.estimate(any())).thenReturn(new OccupancyRecord(id, Instant.EPOCH, result));
        OccupancyController controller = new OccupancyController(estimates, live);

        var response = controller.estimate("A-301", new OccupancyEstimateRequest(null, 700.0, 20, 420.0));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/v1/occupancy-estimates/" + id, response.getHeaders().getLocation().getPath());
        assertEquals("SERVICIO_IA", response.getBody().source().name());
        assertEquals(18, response.getBody().occupancyEstimate());
        verify(estimates).estimate(any());
    }
}
