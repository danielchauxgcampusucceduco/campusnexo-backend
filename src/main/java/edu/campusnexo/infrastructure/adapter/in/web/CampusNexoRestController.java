package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.infrastructure.adapter.contract.ArduinoEnvironmentPayload;
import edu.campusnexo.infrastructure.adapter.contract.EdgeTelemetryPayload;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.SavingsResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.ScenarioResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.SimulationResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TraceResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TwinSpaceResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TwinStateResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST face of the digital twin: state of every space, energy savings, architecture traces, manual
 * controls, HTTP ingestion of edge frames and Arduino readings (same JSON as MQTT) and the live
 * demonstration scenarios.
 */
@RestController
@RequestMapping(ApiPaths.BASE)
class CampusNexoRestController {
    static final String HTTP_INBOUND = "REST · CampusNexoRestController";

    private final TwinQueryUseCase queries;
    private final TwinControlUseCase controls;
    private final ProcessSpaceTelemetryUseCase pipeline;
    private final ProcessEnvironmentReadingUseCase environment;
    private final SimulationUseCase simulation;
    private final Clock clock;
    private final boolean simulationEnabled;

    CampusNexoRestController(TwinQueryUseCase queries, TwinControlUseCase controls, ProcessSpaceTelemetryUseCase pipeline,
                             ProcessEnvironmentReadingUseCase environment, SimulationUseCase simulation, Clock clock,
                             @Value("${app.simulation.enabled}") boolean simulationEnabled) {
        this.queries = queries;
        this.controls = controls;
        this.pipeline = pipeline;
        this.environment = environment;
        this.simulation = simulation;
        this.clock = clock;
        this.simulationEnabled = simulationEnabled;
    }

    @GetMapping("/twin/spaces")
    List<TwinSpaceResponse> twins() {
        return queries.spaces().stream().map(TwinSpaceResponse::from).toList();
    }

    @GetMapping("/twin/spaces/{spaceId}")
    TwinSpaceResponse twin(@PathVariable String spaceId) {
        return TwinSpaceResponse.from(queries.space(spaceId));
    }

    @GetMapping("/twin/savings")
    SavingsResponse savings() {
        return SavingsResponse.from(queries.savings());
    }

    @GetMapping("/twin/traces")
    List<TraceResponse> traces(@RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit) {
        return queries.recentTraces(limit).stream().map(TraceResponse::from).toList();
    }

    /** Edge frame over HTTP, for sites without an MQTT broker. Same JSON as the MQTT topic. */
    @PostMapping("/twin/telemetry")
    TwinStateResponse ingest(@RequestBody EdgeTelemetryPayload payload) {
        return TwinStateResponse.from(pipeline.process(payload.toSnapshot(null, clock.instant()), HTTP_INBOUND));
    }

    /** Room Arduino reading over HTTP, for boards without MQTT. Same JSON as the MQTT topic. */
    @PostMapping("/twin/environment")
    TwinStateResponse environment(@RequestBody ArduinoEnvironmentPayload payload) {
        return TwinStateResponse.from(environment.processEnvironment(payload.toReading(null, clock.instant()),
                HTTP_INBOUND));
    }

    @PostMapping("/twin/spaces/{spaceId}/override")
    TwinStateResponse manualOverride(@PathVariable String spaceId, @RequestParam(defaultValue = "45") long minutes) {
        return TwinStateResponse.from(controls.manualOverride(spaceId, Duration.ofMinutes(minutes)));
    }

    @PostMapping("/twin/spaces/{spaceId}/popup")
    TwinStateResponse openPopUp(@PathVariable String spaceId, @RequestParam(defaultValue = "120") long minutes) {
        return TwinStateResponse.from(controls.openPopUpStudyRoom(spaceId, Duration.ofMinutes(minutes)));
    }

    @PostMapping("/twin/spaces/{spaceId}/maintenance")
    TwinStateResponse maintenance(@PathVariable String spaceId, @RequestParam boolean enabled) {
        return TwinStateResponse.from(controls.setMaintenance(spaceId, enabled));
    }

    @GetMapping("/simulation/scenarios")
    List<ScenarioResponse> scenarios() {
        requireSimulation();
        return Arrays.stream(SimulationScenario.values()).map(ScenarioResponse::from).toList();
    }

    @PostMapping("/simulation/{scenario}")
    SimulationResponse simulate(@PathVariable String scenario) {
        requireSimulation();
        return SimulationResponse.from(simulation.run(parse(scenario)));
    }

    private void requireSimulation() {
        if (!simulationEnabled) {
            throw new ResourceNotFoundException("simulation_disabled", "Los escenarios de simulación están deshabilitados");
        }
    }

    private static SimulationScenario parse(String scenario) {
        try {
            return SimulationScenario.valueOf(scenario.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new DomainValidationException("Escenario desconocido: " + scenario + ". Opciones: "
                    + Arrays.toString(SimulationScenario.values()));
        }
    }
}
