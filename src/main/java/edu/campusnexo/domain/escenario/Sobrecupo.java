package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** More people than the capacity, by booking status or by the room condition. */
final class Sobrecupo implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.SOBRECUPO;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.status() == SpaceStatus.ALERTA_SOBRECUPO || estado.condition() == RoomCondition.SOBRECUPO;
    }
}
