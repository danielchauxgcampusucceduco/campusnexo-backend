package edu.campusnexo.application.port.in;

import edu.campusnexo.application.port.out.AiAdvisorPort.AssignmentPlan;
import edu.campusnexo.application.port.out.AiAdvisorPort.CourseDemand;
import edu.campusnexo.application.port.out.AiAdvisorPort.Forecast;
import java.time.Instant;
import java.util.List;

/** What the administration asks the AI service through the backend. */
public interface AiToolsUseCase {
    /** Rooms for the courses: each one gets the smallest room that covers its attendance. */
    AssignmentPlan asignarSalones(List<CourseDemand> cursos, double margenSeguridad);

    /**
     * Expected occupancy of the room for the next {@code horas}, learned from its stored history.
     *
     * @throws edu.campusnexo.application.exception.ConflictException if the room has no history yet
     */
    Forecast pronostico(String spaceId, int horas);

    /** The room's history as CSV with the columns the AI evaluation reads. */
    String historialCsv(String spaceId, Instant desde, Instant hasta);
}
