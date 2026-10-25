package edu.campusnexo.application.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.port.in.OccupancyStatus;
import edu.campusnexo.application.port.in.SpaceOccupancyStatus;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.InMemoryTelemetryRepository;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiveOccupancyServiceTest {
    private final InMemoryTelemetryRepository telemetry = new InMemoryTelemetryRepository();
    private final LiveOccupancyService service = new LiveOccupancyService(new MutableClock(TestData.NOW),
            new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom(), TestData.library())),
            telemetry, Duration.ofMinutes(30));

    @Test
    @DisplayName("Calcula puestos libres y estado de saturación de la biblioteca")
    void libraryFreeSeats() {
        telemetry.save(TestData.reading("BIB-PISO-2", TestData.NOW.minusSeconds(120), 108, 900, 3));
        List<SpaceOccupancyStatus> result = service.current(SpaceType.BIBLIOTECA);
        assertEquals(1, result.size());
        assertEquals(12, result.get(0).freeSeats());
        assertEquals(90.0, result.get(0).occupancyPct());
        assertEquals(OccupancyStatus.SATURADO, result.get(0).status());
    }

    @Test
    @DisplayName("Sin lecturas recientes el estado es SIN_DATOS y se ordena al final")
    void staleReadingsHaveNoData() {
        telemetry.save(TestData.reading("BIB-PISO-2", TestData.NOW.minus(Duration.ofHours(2)), 10, 600, 1));
        telemetry.save(TestData.reading("AULA-301", TestData.NOW.minusSeconds(60), 8, 600, 1));
        List<SpaceOccupancyStatus> result = service.current(null);
        assertEquals("AULA-301", result.get(0).space().spaceId());
        assertEquals(OccupancyStatus.LIBRE, result.get(0).status());
        assertEquals(OccupancyStatus.SIN_DATOS, result.get(1).status());
        assertTrue(result.get(1).stale());
        assertNull(result.get(1).freeSeats());
    }
}
