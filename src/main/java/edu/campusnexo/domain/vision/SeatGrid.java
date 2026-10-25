package edu.campusnexo.domain.vision;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SeatLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Derives one region of interest per seat from a room's {@link SeatLayout}, in the normalized
 * coordinates of the ceiling camera. The top band (y &lt; {@value #TOP}) is left for the front of the room
 * (board, teacher, door), so people standing there never count as seated. Each seat is its grid cell
 * shrunk by {@value #INSET} on every side, which leaves a gap that the ray casting treats as aisle.
 */
public final class SeatGrid {
    static final double LEFT = 0.05;
    static final double RIGHT = 0.95;
    static final double TOP = 0.30;
    static final double BOTTOM = 0.95;
    static final double INSET = 0.12;

    private SeatGrid() {
    }

    /** Seat regions of the space, or an empty list when it has no seat layout. */
    public static List<StudyZoneRoi> regions(CampusSpace space) {
        SeatLayout layout = space.seatLayout();
        if (layout == null) {
            return List.of();
        }
        double cellWidth = (RIGHT - LEFT) / layout.columns();
        double cellHeight = (BOTTOM - TOP) / layout.rows();
        List<StudyZoneRoi> seats = new ArrayList<>(layout.seats());
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                double x0 = LEFT + column * cellWidth + cellWidth * INSET;
                double x1 = LEFT + (column + 1) * cellWidth - cellWidth * INSET;
                double y0 = TOP + row * cellHeight + cellHeight * INSET;
                double y1 = TOP + (row + 1) * cellHeight - cellHeight * INSET;
                int number = row * layout.columns() + column + 1;
                seats.add(new StudyZoneRoi(
                        zoneIdOf(space.spaceId(), number),
                        space.spaceId(),
                        seatLabel(space) + " " + number,
                        List.of(new NormalizedPoint(x0, y0), new NormalizedPoint(x1, y0),
                                new NormalizedPoint(x1, y1), new NormalizedPoint(x0, y1))));
            }
        }
        return seats;
    }

    /** Region id of seat {@code number} (1-based, row by row from the front): also the workstation code. */
    public static String zoneIdOf(String spaceId, int number) {
        return String.format(Locale.ROOT, "%s-P%02d", spaceId, number);
    }

    /**
     * Whether the point lies in the front band left free of seats, where the teacher stands. The camera frame
     * is normalized with the board at the top, so a person there is in front of the students.
     */
    public static boolean inTeacherZone(NormalizedPoint point) {
        return point.y() < TOP && point.x() >= LEFT && point.x() <= RIGHT;
    }

    private static String seatLabel(CampusSpace space) {
        return switch (space.type()) {
            case AULA, SALA_ESTUDIO -> "Pupitre";
            case SALA_COMPUTO -> "Equipo";
            case LABORATORIO -> "Puesto";
            case BIBLIOTECA, CAFETERIA -> "Mesa";
            default -> "Asiento";
        };
    }
}
