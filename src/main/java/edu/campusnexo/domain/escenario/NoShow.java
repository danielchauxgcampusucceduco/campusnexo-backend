package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** The booking was released because nobody arrived within the grace period. */
final class NoShow implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.NO_SHOW;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.status() == SpaceStatus.LIBERADO_POR_NO_SHOW;
    }
}
