package edu.campusnexo.domain.catalog;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Room code used on campus doors: floor digit, room number and block letter, e.g. {@code 201-A} is
 * room 01 on the second floor of block A. Spaces outside this scheme (old demo spaces) are not room codes.
 */
public record RoomCode(char block, int floor, int room) {
    private static final Pattern FORMAT = Pattern.compile("^(\\d)(\\d{2})-([A-Za-z])$");

    public static boolean matches(String spaceId) {
        return spaceId != null && FORMAT.matcher(spaceId).matches();
    }

    public static RoomCode parse(String spaceId) {
        Matcher matcher = spaceId == null ? null : FORMAT.matcher(spaceId);
        if (matcher == null || !matcher.matches()) {
            throw new DomainValidationException("Código de salón no válido: " + spaceId + " (formato 201-A)");
        }
        return new RoomCode(Character.toUpperCase(matcher.group(3).charAt(0)), Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)));
    }

    /** {@code bloque_a}, as used in MQTT topics. */
    public String blockSegment() {
        return "bloque_" + String.valueOf(block).toLowerCase(Locale.ROOT);
    }

    /** {@code piso_2}, as used in MQTT topics. */
    public String floorSegment() {
        return "piso_" + floor;
    }
}
