package edu.campusnexo.application.port.in;

import edu.campusnexo.application.port.out.AiAdvisorPort.AssignmentPlan;
import edu.campusnexo.application.port.out.AiAdvisorPort.CourseDemand;
import edu.campusnexo.application.port.out.AiAdvisorPort.Forecast;
import edu.campusnexo.application.port.out.AiAdvisorPort.SensorAnomaly;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** What the administration asks the AI service through the backend. */
public interface AiToolsUseCase {
    /** Rooms for the courses that cover their attendance with the least nominal power. */
    AssignmentPlan asignarSalones(List<CourseDemand> cursos, double margenSeguridad);

    /**
     * Sensors that reported implausible values in the last {@code ventana}; each room with one gets a
     * {@code SENSOR_ANOMALO} alert.
     */
    List<SensorAnomaly> saludDeSensores(Duration ventana);

    /**
     * Expected occupancy of the room for the next {@code horas}, learned from its stored history.
     *
     * @throws edu.campusnexo.application.exception.ConflictException if the room has no history yet
     */
    Forecast pronostico(String spaceId, int horas);

    /** The room's history as CSV with the columns the AI evaluation reads. */
    String historialCsv(String spaceId, Instant desde, Instant hasta);
}
