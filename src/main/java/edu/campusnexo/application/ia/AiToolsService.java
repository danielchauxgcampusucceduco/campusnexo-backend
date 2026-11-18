package edu.campusnexo.application.ia;

import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.port.in.AiToolsUseCase;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.out.AiAdvisorPort;
import edu.campusnexo.application.port.out.AiAdvisorPort.AssignmentPlan;
import edu.campusnexo.application.port.out.AiAdvisorPort.CourseDemand;
import edu.campusnexo.application.port.out.AiAdvisorPort.Forecast;
import edu.campusnexo.application.port.out.AiAdvisorPort.RoomOption;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.application.port.out.TwinHistoryPort.OccupancySample;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Prepares what the AI service needs from the campus data (rooms with their capacity, the occupancy history), asks
 * it and returns its proposal. The AI proposes; nothing here changes a booking.
 */
public class AiToolsService implements AiToolsUseCase {
    /** Spaces where courses are taught, the only ones a course can be assigned to. */
    static final Set<SpaceType> TEACHING = EnumSet.of(SpaceType.AULA, SpaceType.SALA_COMPUTO, SpaceType.LABORATORIO,
            SpaceType.AUDITORIO);
    static final Duration HISTORY_WINDOW = Duration.ofDays(28);
    static final int MAX_FORECAST_HOURS = 48;
    static final Duration MAX_EXPORT_WINDOW = Duration.ofDays(31);

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TwinHistoryPort history;
    private final AiAdvisorPort advisor;
    private final String timeZone;

    public AiToolsService(Clock clock, CampusCatalogUseCase catalog, TwinHistoryPort history, AiAdvisorPort advisor,
                          String timeZone) {
        this.clock = clock;
        this.catalog = catalog;
        this.history = history;
        this.advisor = advisor;
        this.timeZone = timeZone;
    }

    @Override
    public AssignmentPlan asignarSalones(List<CourseDemand> cursos, double margenSeguridad) {
        if (cursos.isEmpty()) {
            throw new DomainValidationException("Indica al menos un curso");
        }
        // The service minimizes the sum of each room's cost; with the capacity as the cost it gives every course the
        // smallest room that fits it, which leaves the large rooms free for the courses that need them.
        List<RoomOption> rooms = catalog.listSpaces(null).stream()
                .filter(space -> TEACHING.contains(space.type()))
                .map(space -> new RoomOption(space.spaceId(), space.capacity(), space.capacity()))
                .toList();
        return advisor.assignRooms(cursos, rooms, margenSeguridad);
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
                "space_id,timestamp,people,estimate,std_dev,teachers,students,camera_people,camera_online,emulated\n");
        for (OccupancySample sample : history.occupancy(spaceId, from, to)) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%d,%.3f,%.3f,%d,%d,%s,%b,%b%n", spaceId, sample.at(),
                    sample.people(), sample.estimate(), sample.stdDev(), sample.teachers(), sample.students(),
                    sample.cameraPeople() == null ? "" : sample.cameraPeople(), sample.cameraOnline(), sample.emulated()));
        }
        return csv.toString();
    }
}
