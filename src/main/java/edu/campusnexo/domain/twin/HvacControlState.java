package edu.campusnexo.domain.twin;

/**
 * Supervisory HVAC strategy of a room: <em>when</em> and <em>how hard</em> the unit works. What it does
 * (heat, cool, dehumidify) is the {@code HvacMode} chosen by the climate family. {@link #powerFraction()} is
 * the share of the unit's rated power drawn in that state (illustrative values for energy accounting).
 */
public enum HvacControlState {
    APAGADO(0.0),
    PRE_ENFRIAMIENTO_IA(1.0),
    PRE_CALENTAMIENTO_IA(1.0),
    CONFORT_ACTIVO(1.0),
    ECO_SETBACK_ENTRE_CLASES(0.4),
    CORTE_INERCIA_TERMICA(0.0),
    BLOQUEO_AHORRO_VACIO(0.0),
    VENTILACION_CO2_ACTIVA(0.25),
    OVERRIDE_MANUAL_DOCENTE(1.0),
    /** The room has no climate unit: nothing to control and no energy to save. */
    SIN_CLIMATIZACION(0.0);

    private final double powerFraction;

    HvacControlState(double powerFraction) {
        this.powerFraction = powerFraction;
    }

    public double powerFraction() {
        return powerFraction;
    }

    public boolean compressorOn() {
        return switch (this) {
            case PRE_ENFRIAMIENTO_IA, PRE_CALENTAMIENTO_IA, CONFORT_ACTIVO, ECO_SETBACK_ENTRE_CLASES,
                 OVERRIDE_MANUAL_DOCENTE -> true;
            case APAGADO, CORTE_INERCIA_TERMICA, BLOQUEO_AHORRO_VACIO, VENTILACION_CO2_ACTIVA, SIN_CLIMATIZACION -> false;
        };
    }
}
