package edu.campusnexo.domain.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.RoomCode;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.support.TestData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeatGridTest {

    @Test
    @DisplayName("Un salón de 5 × 6 tiene 30 pupitres numerados fila por fila")
    void classroomGrid() {
        List<StudyZoneRoi> seats = SeatGrid.regions(TestData.room("203-A", SpaceType.AULA, 30, 5, 6, 1));
        assertEquals(30, seats.size());
        assertEquals("203-A-P01", seats.get(0).zoneId());
        assertEquals("Pupitre 30", seats.get(29).label());
    }

    @Test
    @DisplayName("El centro de cada celda está dentro de su pupitre; el frente y los pasillos no")
    void seatsAndAisles() {
        List<StudyZoneRoi> seats = SeatGrid.regions(TestData.room("203-A", SpaceType.AULA, 30, 5, 6, 1));
        StudyZoneRoi first = seats.get(0);
        double cellWidth = (SeatGrid.RIGHT - SeatGrid.LEFT) / 6;
        double cellHeight = (SeatGrid.BOTTOM - SeatGrid.TOP) / 5;
        assertTrue(first.containsPoint(new NormalizedPoint(SeatGrid.LEFT + cellWidth / 2, SeatGrid.TOP + cellHeight / 2)));
        assertFalse(first.containsPoint(new NormalizedPoint(SeatGrid.LEFT + cellWidth, SeatGrid.TOP + cellHeight / 2)));
        NormalizedPoint front = new NormalizedPoint(0.5, 0.15);
        assertTrue(seats.stream().noneMatch(seat -> seat.containsPoint(front)));
    }

    @Test
    @DisplayName("Oficinas sin distribución de puestos no tienen regiones")
    void officeWithoutSeats() {
        CampusSpace office = TestData.room("102-A", SpaceType.ADMINISTRATIVA, 12, null, null, 6);
        assertTrue(SeatGrid.regions(office).isEmpty());
    }

    @Test
    @DisplayName("El código del salón da bloque y piso")
    void roomCode() {
        RoomCode code = RoomCode.parse("305-b");
        assertEquals('B', code.block());
        assertEquals(3, code.floor());
        assertEquals("bloque_b", code.blockSegment());
        assertEquals("piso_3", code.floorSegment());
        assertFalse(RoomCode.matches("AULA-301"));
    }
}
