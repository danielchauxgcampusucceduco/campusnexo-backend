package edu.campusnexo.infrastructure.adapter.out.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RoomTopicTemplateTest {
    @Test
    void derivesBlockAndFloorSegmentsFromCampusRoomCodes() {
        assertEquals("campus/bloque_a/piso_2/201-A/command",
                RoomTopicTemplate.resolve("campus/{bloque}/{piso}/{space_id}/command", "201-A"));
        assertEquals("campus/bloque_b/piso_5/503-B/command",
                RoomTopicTemplate.resolve("campus/{bloque}/{piso}/{space_id}/command", "503-B"));
    }

    @Test
    void usesSafeFallbackSegmentsForNonRoomSpaceIdentifiers() {
        assertEquals("campus/bloque_x/piso_0/BIBLIOTECA/command",
                RoomTopicTemplate.resolve("campus/{bloque}/{piso}/{space_id}/command", "BIBLIOTECA"));
    }
}
