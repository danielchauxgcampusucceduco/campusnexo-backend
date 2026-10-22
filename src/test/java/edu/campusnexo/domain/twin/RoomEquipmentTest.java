package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class RoomEquipmentTest {

    @Test
    void shutdownTurnsOffLoadsAndPreservesTheRoomInventory() {
        RoomEquipment running = new RoomEquipment(true, 450, 3, 8);

        RoomEquipment stopped = running.shutDown();

        assertTrue(running.anythingOn());
        assertFalse(stopped.anythingOn());
        assertEquals(8, stopped.devicesTotal());
        assertEquals(450, stopped.illuminanceLux());
    }

    @Test
    void rejectsMoreActiveDevicesThanTheRoomContains() {
        assertThrows(DomainValidationException.class, () -> new RoomEquipment(false, 0, 3, 2));
    }
}
