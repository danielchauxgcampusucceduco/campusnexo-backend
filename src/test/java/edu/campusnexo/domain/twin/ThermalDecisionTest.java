package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ThermalDecisionTest {

    @Test
    void preservesAnImmutableTrajectoryAndDerivesItsExtrema() {
        var trajectory = new ArrayList<>(java.util.List.of(22.5, 23.0, 21.0));
        ThermalDecision decision = new ThermalDecision(
                HvacControlState.CONFORT_ACTIVO, 22, 10, 5, trajectory, "Confort");
        trajectory.clear();

        assertEquals(3, decision.freeRunningTrajectoryC().size());
        assertEquals(23.0, decision.predictedMaxC());
        assertEquals(21.0, decision.predictedMinC());
    }
}
