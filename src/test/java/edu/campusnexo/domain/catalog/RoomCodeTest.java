package edu.campusnexo.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class RoomCodeTest {

    @Test
    void parsesCampusCodesAndBuildsMqttSegments() {
        RoomCode code = RoomCode.parse("201-a");

        assertEquals(new RoomCode('A', 2, 1), code);
        assertEquals("bloque_a", code.blockSegment());
        assertEquals("piso_2", code.floorSegment());
    }

    @Test
    void distinguishesRoomCodesFromOtherSpaceIds() {
        assertTrue(RoomCode.matches("201-A"));
        assertFalse(RoomCode.matches("BIBLIOTECA"));
        assertFalse(RoomCode.matches(null));
        assertThrows(DomainValidationException.class, () -> RoomCode.parse("BIBLIOTECA"));
    }
}
