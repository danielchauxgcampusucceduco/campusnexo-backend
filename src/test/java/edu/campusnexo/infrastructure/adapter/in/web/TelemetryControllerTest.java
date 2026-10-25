package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.infrastructure.adapter.in.web.dto.TelemetryRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TelemetryControllerTest {
    @Test
    void distinguishesNewReadingsFromIdempotentRetries() {
        TelemetryUseCase telemetry = mock(TelemetryUseCase.class);
        Telemetry recorded = new Telemetry("A-301", Instant.EPOCH, 12, 2, 8, 650, 22, 50, 300, 1.5);
        when(telemetry.ingest(any())).thenReturn(new TelemetryUseCase.IngestResult(recorded, true))
                .thenReturn(new TelemetryUseCase.IngestResult(recorded, false));
        TelemetryController controller = new TelemetryController(telemetry);
        TelemetryRequest request = new TelemetryRequest("A-301", Instant.EPOCH, 12, 2, 8,
                650.0, 22.0, 50.0, 300.0, 1.5);

        var created = controller.ingest(request);
        var duplicate = controller.ingest(request);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.OK, duplicate.getStatusCode());
        assertEquals("A-301", created.getBody().spaceId());
        assertEquals(12, created.getBody().peopleCount());
    }
}
