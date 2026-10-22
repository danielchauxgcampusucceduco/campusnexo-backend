package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.infrastructure.adapter.contract.ArduinoEnvironmentPayload;
import edu.campusnexo.infrastructure.adapter.contract.EdgeTelemetryPayload;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TwinStateResponse;
import java.time.Clock;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter for devices without an MQTT broker: edge camera frames and room Arduino readings over HTTP,
 * with the same JSON as their MQTT topics. Only sensor keys may call it.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/twin")
class TwinIngestionController {
    static final String NAME = "REST · " + TwinIngestionController.class.getSimpleName();

    private final ProcessSpaceTelemetryUseCase pipeline;
    private final ProcessEnvironmentReadingUseCase environment;
    private final Clock clock;

    TwinIngestionController(ProcessSpaceTelemetryUseCase pipeline, ProcessEnvironmentReadingUseCase environment,
                            Clock clock) {
        this.pipeline = pipeline;
        this.environment = environment;
        this.clock = clock;
    }

    @PostMapping("/telemetry")
    TwinStateResponse ingest(@RequestBody EdgeTelemetryPayload payload) {
        return TwinStateResponse.from(pipeline.process(payload.toSnapshot(null, clock.instant()), NAME));
    }

    @PostMapping("/environment")
    TwinStateResponse environment(@RequestBody ArduinoEnvironmentPayload payload) {
        return TwinStateResponse.from(environment.processEnvironment(payload.toReading(null, clock.instant()), NAME));
    }
}
