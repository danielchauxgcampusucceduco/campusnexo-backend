package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThermalDecisionTest {

    @Test
    void preservesAnImmutableTrajectoryAndDerivesItsExtrema() {
        var trajectory = new ArrayList<>(java.util.List.of(22.5, 23.0, 21.0));
        ThermalDecision decision = new ThermalDecision(
                HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, 22, 10, 5, trajectory, "Confort");
        trajectory.clear();

        assertEquals(3, decision.freeRunningTrajectoryC().size());
        assertEquals(23.0, decision.predictedMaxC());
        assertEquals(21.0, decision.predictedMinC());
    }

    @Test
    void rejectsAModeThatContradictsTheStrategy() {
        assertThrows(DomainValidationException.class, () -> new ThermalDecision(HvacControlState.CORTE_INERCIA_TERMICA,
                HvacMode.ENFRIAMIENTO, 22, 10, 5, List.of(22.0), "Inercia"));
        assertThrows(DomainValidationException.class, () -> new ThermalDecision(HvacControlState.CONFORT_ACTIVO,
                HvacMode.APAGADO, 22, 10, 5, List.of(22.0), "Confort"));
    }
}
