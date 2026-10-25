package edu.campusnexo.domain.escenario;

import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;

/**
 * CO₂ above the level where a room needs fresh air, or the climate unit already ventilating for it. Rooms
 * without a climate unit only have the reading, hence the threshold.
 */
final class Co2Alto implements DetectorDeEscenario {
    /** Usual indoor-air guideline for classrooms; above it people notice stuffiness and lose attention. */
    static final double UMBRAL_PPM = 1_000;

    @Override
    public EscenarioOperativo escenario() {
        return EscenarioOperativo.CO2_ALTO;
    }

    @Override
    public boolean reconoce(SpaceTwinState estado, Instant ahora) {
        return estado.co2Ppm() >= UMBRAL_PPM || estado.hvacState() == HvacControlState.VENTILACION_CO2_ACTIVA;
    }
}
