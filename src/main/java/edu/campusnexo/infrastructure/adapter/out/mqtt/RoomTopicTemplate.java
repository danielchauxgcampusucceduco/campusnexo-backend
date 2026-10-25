package edu.campusnexo.infrastructure.adapter.out.mqtt;

import edu.campusnexo.domain.catalog.RoomCode;

/**
 * Fills a command topic template: {@code {space_id}} always, {@code {bloque}} and {@code {piso}} from the
 * room code ({@code 201-A} → {@code bloque_a}, {@code piso_2}). Spaces outside the room-code scheme use
 * {@code bloque_x} and {@code piso_0}.
 */
final class RoomTopicTemplate {
    private RoomTopicTemplate() {
    }

    static String resolve(String template, String spaceId) {
        String block = "bloque_x";
        String floor = "piso_0";
        if (RoomCode.matches(spaceId)) {
            RoomCode code = RoomCode.parse(spaceId);
            block = code.blockSegment();
            floor = code.floorSegment();
        }
        return template.replace("{space_id}", spaceId).replace("{bloque}", block).replace("{piso}", floor);
    }
}
