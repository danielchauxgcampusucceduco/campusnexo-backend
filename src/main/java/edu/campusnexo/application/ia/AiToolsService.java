package edu.campusnexo.application.ia;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.port.in.AiToolsUseCase;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.out.AiAdvisorPort;
import edu.campusnexo.application.port.out.AiAdvisorPort.AssignmentPlan;
import edu.campusnexo.application.port.out.AiAdvisorPort.CourseDemand;
import edu.campusnexo.application.port.out.AiAdvisorPort.Forecast;
import edu.campusnexo.application.port.out.AiAdvisorPort.RoomOption;
import edu.campusnexo.application.port.out.AiAdvisorPort.SensorAnomaly;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.application.port.out.TwinHistoryPort.OccupancySample;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prepares what the AI service needs from the campus data (rooms with their power, readings, history), asks it
 * and turns the answer into alerts where it calls for attention. The AI proposes; nothing here changes a booking
 * or a device.
 */
public class AiToolsService implements AiToolsUseCase {
    /** Spaces where courses are taught, the only ones a course can be assigned to. */
    static final Set<SpaceType> TEACHING = EnumSet.of(SpaceType.AULA, SpaceType.SALA_COMPUTO, SpaceType.LABORATORIO,
            SpaceType.AUDITORIO);
    static final int MAX_READINGS = 20_000;
    static final Duration HISTORY_WINDOW = Duration.ofDays(28);
    static final int MAX_FORECAST_HOURS = 48;
    static final Duration MAX_EXPORT_WINDOW = Duration.ofDays(31);

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TelemetryRepository telemetry;
    private final TwinHistoryPort history;
    private final AiAdvisorPort advisor;
    private final EnergyAccountingPolicy energy;
    private final AlertRecorder alerts;
    private final Duration cooldown;
    private final String timeZone;

    public AiToolsService(Clock clock, CampusCatalogUseCase catalog, TelemetryRepository telemetry,
                          TwinHistoryPort history, AiAdvisorPort advisor, EnergyAccountingPolicy energy,
                          AlertRecorder alerts, Duration cooldown, String timeZone) {
        this.clock = clock;
        this.catalog = catalog;
        this.telemetry = telemetry;
        this.history = history;
        this.advisor = advisor;
        this.energy = energy;
        this.alerts = alerts;
        this.cooldown = cooldown;
        this.timeZone = timeZone;
    }

    @Override
    public AssignmentPlan asignarSalones(List<CourseDemand> cursos, double margenSeguridad) {
        if (cursos.isEmpty()) {
            throw new DomainValidationException("Indica al menos un curso");
        }
        // A room without a climate unit only draws its lights: a tenth of the rated power of a climatized one.
        List<RoomOption> rooms = catalog.listSpaces(null).stream()
                .filter(space -> TEACHING.contains(space.type()))
                .map(space -> new RoomOption(space.spaceId(), space.capacity(),
                        round(energy.ratedKw(space.areaSqm()) * (space.hvacInstalled() ? 1 : 0.1))))
                .toList();
        return advisor.assignRooms(cursos, rooms, margenSeguridad);
    }

    @Override
    public List<SensorAnomaly> saludDeSensores(Duration ventana) {
        Instant now = clock.instant();
        List<Telemetry> readings = telemetry.findSince(now.minus(ventana), MAX_READINGS);
        if (readings.isEmpty()) {
            return List.of();
        }
        List<SensorAnomaly> anomalies = advisor.detectAnomalies(readings);
        Map<String, List<SensorAnomaly>> bySpace = anomalies.stream()
                .collect(Collectors.groupingBy(SensorAnomaly::spaceId));
        bySpace.forEach((spaceId, found) -> {
            SensorAnomaly first = found.getFirst();
            alerts.recordUnlessRaisedSince(Alert.raise(spaceId, AlertType.SENSOR_ANOMALO, AlertSeverity.ADVERTENCIA,
                    now, found.size() + " lecturas anómalas; la primera: " + first.sensor() + " " + first.kind()
                            + " (" + first.detail() + ")", found.size(), 0), now.minus(cooldown));
        });
        return anomalies;
    }

    @Override
    public Forecast pronostico(String spaceId, int horas) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        if (horas < 1 || horas > MAX_FORECAST_HOURS) {
            throw new DomainValidationException("horas debe estar entre 1 y " + MAX_FORECAST_HOURS);
        }
        Instant now = clock.instant();
        List<OccupancySample> samples = history.occupancy(spaceId, now.minus(HISTORY_WINDOW), now);
        if (samples.isEmpty()) {
            throw new ConflictException("history_not_available", history.available()
                    ? "El espacio " + spaceId + " aún no tiene historial para pronosticar"
                    : "El historial (MongoDB) no está configurado");
        }
        return advisor.forecastOccupancy(spaceId, space.capacity(), samples, horas, timeZone);
    }

    @Override
    public String historialCsv(String spaceId, Instant desde, Instant hasta) {
        catalog.getRequiredSpace(spaceId);
        Instant to = hasta == null ? clock.instant() : hasta;
        Instant from = desde == null ? to.minus(Duration.ofDays(1)) : desde;
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_EXPORT_WINDOW) > 0) {
            throw new DomainValidationException("La ventana debe ser positiva y de máximo "
                    + MAX_EXPORT_WINDOW.toDays() + " días");
        }
        StringBuilder csv = new StringBuilder(
                "space_id,timestamp,people,estimate,std_dev,teachers,students,camera_people,co2_ppm,camera_online,emulated\n");
        for (OccupancySample sample : history.occupancy(spaceId, from, to)) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%d,%.3f,%.3f,%d,%d,%s,%.1f,%b,%b%n", spaceId, sample.at(),
                    sample.people(), sample.estimate(), sample.stdDev(), sample.teachers(), sample.students(),
                    sample.cameraPeople() == null ? "" : sample.cameraPeople(), sample.co2Ppm(),
                    sample.cameraOnline(), sample.emulated()));
        }
        return csv.toString();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
