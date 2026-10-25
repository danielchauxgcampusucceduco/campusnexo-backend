package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;

/** Classifies a room from the people estimated by the occupancy filter and its equipment state. */
public class RoomConditionPolicy {

    public RoomCondition classify(boolean maintenance, int people, int capacity, RoomEquipment equipment) {
        requireNonNull(equipment, "equipment");
        if (maintenance) {
            return RoomCondition.MANTENIMIENTO;
        }
        if (people > capacity) {
            return RoomCondition.SOBRECUPO;
        }
        if (people > 0) {
            return RoomCondition.OCUPADO;
        }
        boolean devices = equipment.devicesOn() > 0;
        if (equipment.lightsOn() && devices) {
            return RoomCondition.LUCES_Y_EQUIPOS_SIN_USO;
        }
        if (equipment.lightsOn()) {
            return RoomCondition.LUCES_SIN_OCUPACION;
        }
        return devices ? RoomCondition.EQUIPOS_SIN_USO : RoomCondition.LIBRE;
    }
}
