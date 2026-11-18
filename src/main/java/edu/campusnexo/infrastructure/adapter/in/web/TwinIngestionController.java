package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.infrastructure.adapter.contract.EdgeTelemetryPayload;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TwinStateResponse;
import java.time.Clock;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter for edge cameras without an MQTT broker: the frames over HTTPS, with the same JSON as their MQTT
 * topic. Only sensor keys may call it.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/twin")
class TwinIngestionController {
    static final String NAME = "REST · " + TwinIngestionController.class.getSimpleName();

    private final ProcessSpaceTelemetryUseCase pipeline;
    private final Clock clock;

    TwinIngestionController(ProcessSpaceTelemetryUseCase pipeline, Clock clock) {
        this.pipeline = pipeline;
        this.clock = clock;
    }

    @PostMapping("/telemetry")
    TwinStateResponse ingest(@RequestBody EdgeTelemetryPayload payload) {
        return TwinStateResponse.from(pipeline.process(payload.toSnapshot(null, clock.instant()), NAME));
    }
}
