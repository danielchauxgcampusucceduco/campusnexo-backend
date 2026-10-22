package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class SimulationControllerTest {
    @Test
    void existsOnlyWhenTheDemonstrationIsEnabled() {
        ConditionalOnProperty condition = SimulationController.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("app.simulation.enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
    }

    @Test
    void acceptsCaseAndHyphenVariantsAndNamesItselfInTheTrace() {
        SimulationUseCase simulations = mock(SimulationUseCase.class);
        when(simulations.run(SimulationScenario.CLASE_EN_CURSO, SimulationController.NAME))
                .thenReturn(new SimulationResult(SimulationScenario.CLASE_EN_CURSO, List.of(), null));

        var response = new SimulationController(simulations).simulate("clase-en-curso");

        assertEquals("CLASE_EN_CURSO", response.scenario().name());
        assertEquals("REST · SimulationController", SimulationController.NAME);
        verify(simulations).run(SimulationScenario.CLASE_EN_CURSO, SimulationController.NAME);
    }

    @Test
    void rejectsUnknownScenarios() {
        assertThrows(DomainValidationException.class, () -> SimulationController.parse("NO_EXISTE"));
    }

    @Test
    void listsAllThirteenScenarios() {
        assertEquals(13, new SimulationController(mock(SimulationUseCase.class)).scenarios().size());
    }
}
