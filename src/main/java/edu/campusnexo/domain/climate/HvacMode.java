package edu.campusnexo.domain.climate;

/** What the climate unit does: heat, cool, dehumidify or nothing. */
public enum HvacMode {
    APAGADO,
    CALEFACCION,
    ENFRIAMIENTO,
    DESHUMIDIFICACION;

    public boolean isActive() {
        return this != APAGADO;
    }

    /** Heating when the target is warmer than the room, cooling otherwise. */
    public static HvacMode toward(double indoorC, double targetC) {
        return targetC > indoorC ? CALEFACCION : ENFRIAMIENTO;
    }
}
