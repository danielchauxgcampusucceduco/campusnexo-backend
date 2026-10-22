package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.ScenarioResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.SimulationResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live demonstration scenarios (administrators only). The controller exists only with
 * {@code app.simulation.enabled=true}; otherwise its routes are not mapped at all and answer 404.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/simulation")
@ConditionalOnProperty(name = "app.simulation.enabled", havingValue = "true")
class SimulationController {
    static final String NAME = "REST · " + SimulationController.class.getSimpleName();

    private final SimulationUseCase simulation;

    SimulationController(SimulationUseCase simulation) {
        this.simulation = simulation;
    }

    @GetMapping("/scenarios")
    List<ScenarioResponse> scenarios() {
        return Arrays.stream(SimulationScenario.values()).map(ScenarioResponse::from).toList();
    }

    @PostMapping("/{scenario}")
    SimulationResponse simulate(@PathVariable String scenario) {
        return SimulationResponse.from(simulation.run(parse(scenario), NAME));
    }

    static SimulationScenario parse(String scenario) {
        try {
            return SimulationScenario.valueOf(scenario.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new DomainValidationException("Escenario desconocido: " + scenario + ". Opciones: "
                    + Arrays.toString(SimulationScenario.values()));
        }
    }
}
