package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampusNexoRestControllerTest {
    @Test
    void disabledScenariosAreNotDiscoverableOrExecutable() {
        SimulationUseCase simulations = mock(SimulationUseCase.class);
        var controller = controller(simulations, false);

        assertThrows(ResourceNotFoundException.class, controller::scenarios);
        assertThrows(ResourceNotFoundException.class, () -> controller.simulate("CLASE_EN_CURSO"));
        verifyNoInteractions(simulations);
    }

    @Test
    void acceptsCaseAndHyphenVariantsForEnabledScenarioNames() {
        SimulationUseCase simulations = mock(SimulationUseCase.class);
        when(simulations.run(SimulationScenario.CLASE_EN_CURSO))
                .thenReturn(new SimulationResult(SimulationScenario.CLASE_EN_CURSO, List.of(), null));
        var response = controller(simulations, true).simulate("clase-en-curso");

        assertEquals("CLASE_EN_CURSO", response.scenario().name());
        verify(simulations).run(SimulationScenario.CLASE_EN_CURSO);
    }

    private static CampusNexoRestController controller(SimulationUseCase simulations, boolean enabled) {
        return new CampusNexoRestController(mock(TwinQueryUseCase.class), mock(TwinControlUseCase.class),
                mock(ProcessSpaceTelemetryUseCase.class), mock(ProcessEnvironmentReadingUseCase.class),
                simulations, Clock.systemUTC(), enabled);
    }
}
