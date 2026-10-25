package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Duration;
import java.time.Instant;

/**
 * A room that has a camera and lost it: the camera reported itself offline, or stopped sending frames. Rooms
 * that never had a camera are not failing.
 */
final class FalloCamara implements DetectorDeEscenario {
    /** Frames arrive every few seconds; this long without one means the camera or its link is down. */
    static final Duration SIN_SENAL = Duration.ofMinutes(2);

    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.FALLO_CAMARA;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        Instant lastFrame = estado.sensing().lastCameraFrameAt();
        return lastFrame != null && (!estado.cameraOnline() || lastFrame.isBefore(ahora.minus(SIN_SENAL)));
    }
}
