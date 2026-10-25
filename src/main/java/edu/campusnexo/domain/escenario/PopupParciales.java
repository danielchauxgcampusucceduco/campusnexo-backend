package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** The room is open as a temporary study room. */
final class PopupParciales implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.POPUP_PARCIALES;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.status() == SpaceStatus.HABILITADO_POPUP_PARCIALES || estado.popUpActive(ahora);
    }
}
