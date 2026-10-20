package edu.campusnexo.domain.twin;

/**
 * Overall state of a room as staff see it on the campus map: whether there are people and whether
 * lights or computers are wasting energy in an empty room.
 */
public enum RoomCondition {
    /** Nobody inside and everything off. */
    LIBRE,
    /** People inside, within capacity. */
    OCUPADO,
    /** More people than seats. */
    SOBRECUPO,
    /** Empty room with the lights on. */
    LUCES_SIN_OCUPACION,
    /** Empty room with computers on. */
    EQUIPOS_SIN_USO,
    /** Empty room with lights and computers on. */
    LUCES_Y_EQUIPOS_SIN_USO,
    /** Out of service. */
    MANTENIMIENTO;

    public boolean wastesEnergy() {
        return this == LUCES_SIN_OCUPACION || this == EQUIPOS_SIN_USO || this == LUCES_Y_EQUIPOS_SIN_USO;
    }
}
