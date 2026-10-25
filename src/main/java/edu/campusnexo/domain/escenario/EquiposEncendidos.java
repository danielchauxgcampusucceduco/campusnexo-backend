package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** Computers on in an empty room. */
final class EquiposEncendidos implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.EQUIPOS_ENCENDIDOS;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.condition() == RoomCondition.EQUIPOS_SIN_USO
                || estado.condition() == RoomCondition.LUCES_Y_EQUIPOS_SIN_USO;
    }
}
