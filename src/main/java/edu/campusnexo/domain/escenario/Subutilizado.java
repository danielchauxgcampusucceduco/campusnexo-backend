package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** A booking in use by far fewer people than expected. */
final class Subutilizado implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.SUBUTILIZADO;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.status() == SpaceStatus.ALERTA_SUBUTILIZADO;
    }
}
