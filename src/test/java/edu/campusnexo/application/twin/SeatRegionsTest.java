package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatRegionsTest {
    @Test
    void prefersHandDrawnRegionsOverTheDerivedSeatGrid() {
        var room = TestData.room("203-A", SpaceType.AULA, 6, 2, 3, 0);
        StudyZoneRoi drawn = new StudyZoneRoi("CUSTOM-1", room.spaceId(), "Puesto especial", List.of(
                new NormalizedPoint(0.1, 0.1), new NormalizedPoint(0.8, 0.1), new NormalizedPoint(0.5, 0.8)));
        var repository = new InMemorySpaceRepositoryPort().withRois(room.spaceId(), List.of(drawn));

        List<StudyZoneRoi> result = new SeatRegions(repository).of(room);

        assertEquals(1, result.size());
        assertSame(drawn, result.getFirst());
    }

    @Test
    void derivesCameraRegionsWhenNoHandDrawnRoisExist() {
        var room = TestData.room("203-A", SpaceType.AULA, 6, 2, 3, 0);

        var result = new SeatRegions(new InMemorySpaceRepositoryPort()).of(room);

        assertEquals(6, result.size());
        assertEquals("203-A-P01", result.getFirst().zoneId());
    }
}
