package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** Lights on in an empty room. */
final class LucesEncendidas implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.LUCES_ENCENDIDAS;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.condition() == RoomCondition.LUCES_SIN_OCUPACION
                || estado.condition() == RoomCondition.LUCES_Y_EQUIPOS_SIN_USO;
    }
}
