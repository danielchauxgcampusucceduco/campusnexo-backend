package edu.campusnexo.application.port.in;

/** Live demonstration scenarios. Each one goes through the same use cases as real telemetry. */
public enum SimulationScenario {
    CLASE_EN_CURSO("203-A", "Clase con 26 estudiantes en sus pupitres; la inercia térmica permite apagar la calefacción"),
    CO2_ALTO("302-A", "Salón lleno con 1450 ppm de CO2: la ventilación pasa a tener prioridad"),
    FALLO_CAMARA("203-A", "La cámara se desconecta; el sistema sigue estimando con CO2 y Wi-Fi"),
    SOBRECUPO("103-B", "31 personas en un laboratorio con 24 puestos"),
    NO_SHOW("304-A", "Reserva iniciada hace 20 min sin asistentes: se libera el salón y se apaga el clima"),
    SUBUTILIZADO("401-A", "Reserva para 30 personas con solo 6 asistentes"),
    POPUP_PARCIALES("503-A", "Semana de parciales: el salón libre se abre como sala de estudio"),
    LUCES_ENCENDIDAS("204-A", "Salón vacío hace 8 min con las luces encendidas: el Arduino las apaga"),
    EQUIPOS_ENCENDIDOS("302-B", "Sala de cómputo vacía hace 12 min con 30 equipos encendidos: se suspenden"),
    OVERRIDE_DOCENTE("202-A", "El docente toma el control manual del clima por 45 min"),
    MANTENIMIENTO("104-B", "El laboratorio entra en mantenimiento"),
    ACAPARAMIENTO_BIBLIOTECA("201-B", "Mesas con pertenencias sin estudiante: alerta y liberación a los 15 min"),
    REINICIAR("*", "Restablece todos los salones y elimina las reservas de simulación");

    private final String spaceId;
    private final String description;

    SimulationScenario(String spaceId, String description) {
        this.spaceId = spaceId;
        this.description = description;
    }

    public String spaceId() {
        return spaceId;
    }

    public String description() {
        return description;
    }
}
