package edu.campusnexo.application.port.in;

/** Live demonstration scenarios. Each one goes through the same use cases as real telemetry. */
public enum SimulationScenario {
    CLASE_EN_CURSO("AULA-301", "Clase reservada con asistencia normal; la inercia térmica permite cortar el compresor"),
    CO2_ALTO("AULA-301", "Aula llena con 1450 ppm de CO2: la ventilación pasa a tener prioridad"),
    FALLO_CAMARA("AULA-301", "La cámara se desconecta; Kalman sigue estimando con CO2 y Wi-Fi"),
    NO_SHOW("AULA-MAGNA-204", "Reserva iniciada hace 20 min sin asistentes: se libera y se bloquea el HVAC"),
    PRE_ENFRIAMIENTO("AULA-MAGNA-204", "Aula tropical vacía a 28,5 °C con clase en 20 min: pre-enfriamiento"),
    OVERRIDE_DOCENTE("AULA-MAGNA-204", "El docente toma el control manual del aire por 45 min"),
    SOBRECUPO("LAB-IA-102", "31 personas en un laboratorio con aforo de 24"),
    MANTENIMIENTO("LAB-IA-102", "El laboratorio entra en mantenimiento"),
    SUBUTILIZADO("AULA-ADM-101", "Reserva para 30 personas con solo 6 asistentes"),
    POPUP_PARCIALES("AULA-ADM-101", "Semana de parciales: el aula libre se abre como sala de estudio pop-up"),
    ACAPARAMIENTO_BIBLIOTECA("BIB-PISO-2", "Puestos con pertenencias sin estudiante: alerta y liberación a los 15 min"),
    REINICIAR("*", "Restablece todos los espacios y elimina las reservas de simulación");

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
