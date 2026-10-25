package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class ThermalParametersTest {

    @Test
    void providesTheDocumentedReferenceControlParameters() {
        ThermalParameters parameters = ThermalParameters.defaults();

        assertEquals(120, parameters.timeConstantMinutes());
        assertEquals(5, parameters.stepMinutes());
        assertEquals(1_000, parameters.co2VentilationPpm());
    }

    @Test
    void rejectsAZeroSimulationStep() {
        ThermalParameters defaults = ThermalParameters.defaults();
        assertThrows(DomainValidationException.class, () -> new ThermalParameters(
                defaults.timeConstantMinutes(), defaults.occupantGainCPerHour(), defaults.hvacRateCPerMinute(),
                0, defaults.defaultOccupiedMinutes(), defaults.preConditioningLeadMinutes(),
                defaults.ecoSetbackWindowMinutes(), defaults.ecoSetbackOffsetC(), defaults.co2VentilationPpm()));
    }
}
