package edu.campusnexo.application.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.port.out.AiAdvisorPort;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiToolsServiceTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final List<TwinHistoryPort.OccupancySample> history = new ArrayList<>();
    private final RecordingAdvisor advisor = new RecordingAdvisor();
    private final AiToolsService service = new AiToolsService(clock, new SpaceCatalogService(
            new InMemorySpaceCatalogRepository(TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30),
                    TestData.room("303-A", SpaceType.AULA, 36, 6, 6, 1),
                    TestData.room("1A-RECTORIA", SpaceType.ADMINISTRATIVA, 4, null, null, 4))),
            new History(), advisor, "America/Bogota");

    @Test
    @DisplayName("La asignación solo ofrece espacios de clase, y su costo es el aforo: el salón más chico que alcanza")
    void assignmentOffersTeachingRoomsCostedByCapacity() {
        service.asignarSalones(List.of(new AiAdvisorPort.CourseDemand("IS-701", 25)), 1.1);
        assertEquals(List.of("301-A", "303-A"), advisor.rooms.stream().map(AiAdvisorPort.RoomOption::roomId).sorted().toList());
        assertTrue(advisor.rooms.stream().allMatch(room -> room.powerKw() == room.capacity()));
        assertThrows(DomainValidationException.class, () -> service.asignarSalones(List.of(), 1.1));
    }

    @Test
    @DisplayName("El pronóstico necesita historial y unas horas razonables")
    void forecastNeedsHistory() {
        assertThrows(ConflictException.class, () -> service.pronostico("301-A", 12));
        history.add(new TwinHistoryPort.OccupancySample(TestData.NOW.minusSeconds(3_600), "301-A", 12, 12, 1, 1, 11, 12,
                true, true, List.of("CLASE_EN_CURSO")));
        assertEquals("301-A", service.pronostico("301-A", 12).spaceId());
        assertThrows(DomainValidationException.class, () -> service.pronostico("301-A", 100));
    }

    @Test
    @DisplayName("El historial sale en CSV con las columnas que lee la evaluación de la IA")
    void historyAsCsv() {
        history.add(new TwinHistoryPort.OccupancySample(TestData.NOW.minusSeconds(60), "301-A", 12, 12.4, 1.1, 1, 11,
                null, false, true, List.of()));
        String csv = service.historialCsv("301-A", null, null);
        assertTrue(csv.startsWith("space_id,timestamp,people,estimate,std_dev,teachers,students,camera_people,camera_online"));
        assertTrue(csv.contains("301-A," + TestData.NOW.minusSeconds(60) + ",12,12.400,1.100,1,11,,false,true"));
    }

    private final class History implements TwinHistoryPort {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<OccupancySample> occupancy(String spaceId, Instant from, Instant to) {
            return history.stream().filter(s -> s.spaceId().equals(spaceId) && !s.at().isBefore(from) && s.at().isBefore(to))
                    .toList();
        }
    }

    private static final class RecordingAdvisor implements AiAdvisorPort {
        List<RoomOption> rooms = List.of();

        @Override
        public AssignmentPlan assignRooms(List<CourseDemand> courses, List<RoomOption> rooms, double safetyMargin) {
            this.rooms = rooms;
            return new AssignmentPlan(true, List.of(), 0.0, null);
        }

        @Override
        public Forecast forecastOccupancy(String spaceId, int capacity, List<TwinHistoryPort.OccupancySample> samples,
                                          int horizonHours, String timeZone) {
            return new Forecast(spaceId, "prueba", samples.size(), List.of(), null);
        }
    }
}
