package edu.campusnexo.domain.escenario;

/**
 * Situations the administration wants to see in a room, recognized from what its devices report. Values are
 * kept in Spanish, as users see them; {@link #descripcion()} is the sentence shown next to the room.
 */
public enum EscenarioOperativo {
    CLASE_EN_CURSO("Docente frente al tablero y estudiantes en sus puestos"),
    FALLO_CAMARA("La cámara no reporta; la ocupación es la última estimada y su margen crece"),
    SOBRECUPO("Más personas que el aforo del espacio"),
    NO_SHOW("Reserva liberada: nadie llegó a tiempo"),
    SUBUTILIZADO("Reserva en uso con muy pocos asistentes"),
    POPUP_PARCIALES("Habilitado como sala de estudio temporal"),
    MANTENIMIENTO("Fuera de servicio por mantenimiento"),
    ACAPARAMIENTO("Puestos con pertenencias y sin persona");

    private final String descripcion;

    EscenarioOperativo(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }
}
