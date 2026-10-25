package edu.campusnexo.domain.port;

import edu.campusnexo.domain.twin.EquipmentCommand;

/** Sends lighting and computer shutdown orders to the room's Arduino. */
public interface RoomEquipmentCommandPort {
    void send(EquipmentCommand command);
}
