package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** The teacher took manual control of the climate. */
final class OverrideDocente implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.OVERRIDE_DOCENTE;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.hvacState() == HvacControlState.OVERRIDE_MANUAL_DOCENTE || estado.overrideActive(ahora);
    }
}
