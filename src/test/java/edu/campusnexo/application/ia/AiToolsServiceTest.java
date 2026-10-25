package edu.campusnexo.application.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.port.out.AiAdvisorPort;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.InMemoryTelemetryRepository;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiToolsServiceTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final InMemoryTelemetryRepository telemetry = new InMemoryTelemetryRepository();
    private final InMemoryAlertRepository alerts = new InMemoryAlertRepository();
    private final List<TwinHistoryPort.OccupancySample> history = new ArrayList<>();
    private final RecordingAdvisor advisor = new RecordingAdvisor();
    private final AiToolsService service = new AiToolsService(clock, new SpaceCatalogService(
            new InMemorySpaceCatalogRepository(TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30),
                    TestData.room("303-A", SpaceType.AULA, 36, 6, 6, 1),
                    TestData.room("1A-RECTORIA", SpaceType.ADMINISTRATIVA, 4, null, null, 4))),
            telemetry, new History(), advisor, new EnergyAccountingPolicy(0.12, Duration.ofMinutes(15),
            LocalTime.of(7, 0), LocalTime.of(21, 0), ZoneId.of("America/Bogota")), new AlertRecorder(alerts),
            Duration.ofMinutes(30), "America/Bogota");

    @Test
    @DisplayName("La asignación solo ofrece espacios de clase, con su potencia nominal")
    void assignmentOffersTeachingRoomsWithTheirPower() {
        service.asignarSalones(List.of(new AiAdvisorPort.CourseDemand("IS-701", 25)), 1.1);
        assertEquals(List.of("301-A", "303-A"), advisor.rooms.stream().map(AiAdvisorPort.RoomOption::roomId).sorted().toList());
        assertTrue(advisor.rooms.stream().allMatch(room -> room.powerKw() > 0));
        assertThrows(DomainValidationException.class, () -> service.asignarSalones(List.of(), 1.1));
    }

    @Test
    @DisplayName("Las lecturas anómalas que encuentra la IA dejan una alerta por salón")
    void anomaliesRaiseOneAlertPerRoom() {
        assertTrue(service.saludDeSensores(Duration.ofHours(24)).isEmpty(), "sin lecturas no se pregunta a la IA");
        telemetry.save(TestData.reading("301-A", TestData.NOW.minusSeconds(60), 10, 9_000, 1.0));
        advisor.anomalies = List.of(
                new AiAdvisorPort.SensorAnomaly("301-A", "co2_ppm", TestData.NOW, "FUERA_DE_RANGO", 9_000, "> 5000"),
                new AiAdvisorPort.SensorAnomaly("301-A", "co2_ppm", TestData.NOW, "SALTO", 9_000, "salto"));
        assertEquals(2, service.saludDeSensores(Duration.ofHours(24)).size());
        assertEquals(List.of(AlertType.SENSOR_ANOMALO), alerts.rows().stream().map(a -> a.type()).toList());
    }

    @Test
    @DisplayName("El pronóstico necesita historial y unas horas razonables")
    void forecastNeedsHistory() {
        assertThrows(ConflictException.class, () -> service.pronostico("301-A", 12));
        history.add(new TwinHistoryPort.OccupancySample(TestData.NOW.minusSeconds(3_600), "301-A", 12, 12, 1, 1, 11, 12,
                800, true, true, List.of("CLASE_EN_CURSO")));
        assertEquals("301-A", service.pronostico("301-A", 12).spaceId());
        assertThrows(DomainValidationException.class, () -> service.pronostico("301-A", 100));
    }

    @Test
    @DisplayName("El historial sale en CSV con las columnas que lee la evaluación de la IA")
    void historyAsCsv() {
        history.add(new TwinHistoryPort.OccupancySample(TestData.NOW.minusSeconds(60), "301-A", 12, 12.4, 1.1, 1, 11,
                null, 800, false, true, List.of()));
        String csv = service.historialCsv("301-A", null, null);
        assertTrue(csv.startsWith("space_id,timestamp,people,estimate,std_dev,teachers,students,camera_people,"));
        assertTrue(csv.contains("301-A," + TestData.NOW.minusSeconds(60) + ",12,12.400,1.100,1,11,,800.0,false,true"));
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
        List<SensorAnomaly> anomalies = List.of();

        @Override
        public AssignmentPlan assignRooms(List<CourseDemand> courses, List<RoomOption> rooms, double safetyMargin) {
            this.rooms = rooms;
            return new AssignmentPlan(true, List.of(), 0.0, null);
        }

        @Override
        public List<SensorAnomaly> detectAnomalies(List<Telemetry> readings) {
            return anomalies;
        }

        @Override
        public Forecast forecastOccupancy(String spaceId, int capacity, List<TwinHistoryPort.OccupancySample> samples,
                                          int horizonHours, String timeZone) {
            return new Forecast(spaceId, "prueba", samples.size(), List.of(), null);
        }
    }
}
