package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.vision.ZoneDeskState;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** Desks held by belongings without a person. */
final class Acaparamiento implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.ACAPARAMIENTO;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.desks().stream().anyMatch(desk -> desk.state() == ZoneDeskState.ALERTA_ACAPARAMIENTO
                || desk.state() == ZoneDeskState.LIBERADO_POR_ABANDONO);
    }
}
