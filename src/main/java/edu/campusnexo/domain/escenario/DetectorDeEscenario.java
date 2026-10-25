package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/**
 * Recognizes one operational scenario in the state of a room (Strategy pattern: one class per scenario). The
 * detectors only read decisions the twin already took, so they need no configuration and never disagree with
 * what the room shows.
 */
public interface DetectorDeEscenario {
    EscenarioOperativo escenario();

    boolean reconoce(SpaceTwinState estado, Instant ahora);
}
