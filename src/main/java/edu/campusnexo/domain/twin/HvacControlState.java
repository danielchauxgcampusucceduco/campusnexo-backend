package edu.campusnexo.domain.twin;

/**
 * Supervisory HVAC strategy chosen by the thermal-inertia controller. {@link #powerFraction()} is the
 * share of the unit's rated power drawn in that state (illustrative values for energy accounting).
 */
public enum HvacControlState {
    APAGADO(0.0),
    PRE_ENFRIAMIENTO_IA(1.0),
    CONFORT_ACTIVO(1.0),
    ECO_SETBACK_ENTRE_CLASES(0.4),
    CORTE_INERCIA_TERMICA(0.0),
    BLOQUEO_AHORRO_VACIO(0.0),
    VENTILACION_CO2_ACTIVA(0.25),
    OVERRIDE_MANUAL_DOCENTE(1.0);

    private final double powerFraction;

    HvacControlState(double powerFraction) {
        this.powerFraction = powerFraction;
    }

    public double powerFraction() {
        return powerFraction;
    }

    public boolean compressorOn() {
        return this == PRE_ENFRIAMIENTO_IA || this == CONFORT_ACTIVO || this == ECO_SETBACK_ENTRE_CLASES
                || this == OVERRIDE_MANUAL_DOCENTE;
    }
}
