package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.SeatGrid;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PresenceTest {
    private static TrackedDetection person(long id, double x, double y) {
        return new TrackedDetection(id, DetectedObjectClass.PERSON, 0.9, new BoundingBox(x - 0.02, y - 0.04, x + 0.02, y + 0.04));
    }

    @Test
    @DisplayName("Profesor frente al tablero y estudiantes al fondo: uno en la zona docente, el resto en sus pupitres")
    void teacherInFrontStudentsAtTheBack() {
        List<StudyZoneRoi> seats = SeatGrid.regions(TestData.room("303-A", SpaceType.AULA, 36, 6, 6, 1));
        List<TrackedDetection> frame = new ArrayList<>();
        frame.add(person(1, 0.50, 0.15));
        // Students in the three back rows: the centers of the cells of rows 4 to 6.
        double cellWidth = 0.90 / 6;
        double cellHeight = 0.65 / 6;
        long id = 2;
        for (int row = 3; row < 6; row++) {
            for (int column = 0; column < 6; column += 2) {
                frame.add(person(id++, 0.05 + cellWidth * (column + 0.5), 0.30 + cellHeight * (row + 0.5)));
            }
        }
        List<DeskObservation> desks = seats.stream()
                .map(seat -> seat.evaluateDeskGhosting(frame, null, TestData.NOW, Duration.ofMinutes(15))).toList();

        Presence presence = Presence.of(frame, desks);

        assertEquals(1, presence.teachers());
        assertEquals(9, presence.students());
        assertTrue(presence.teacherPresent());
    }

    @Test
    @DisplayName("Un salón sin pupitres no distingue roles")
    void roomsWithoutSeatsHaveNoRoles() {
        Presence presence = Presence.of(List.of(person(1, 0.5, 0.1)), List.of());
        assertEquals(Presence.none(), presence);
        assertFalse(presence.teacherPresent());
    }
}
