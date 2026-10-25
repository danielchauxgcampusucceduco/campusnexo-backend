package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/** A class needs a teacher in front of the board and at least one student seated. */
final class ClaseEnCurso implements DetectorDeEscenario {
    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.CLASE_EN_CURSO;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.presence().teacherPresent() && estado.presence().students() > 0;
    }
}
