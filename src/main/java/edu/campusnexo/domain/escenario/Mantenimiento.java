package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** Staff took the room out of service. */
final class Mantenimiento implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.MANTENIMIENTO;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.maintenance();
    }
}
