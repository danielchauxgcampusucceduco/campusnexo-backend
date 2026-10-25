package edu.campusnexo.application.port.out;

import edu.campusnexo.application.port.out.TwinHistoryPort.OccupancySample;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;
import java.util.List;

/**
 * The machine-learning tools of the AI service that the administration uses: which room each course should
 * take, which sensors report implausible values, and how full a room will be in the next hours. Only the
 * backend talks to the AI service; the dashboard never does.
 *
 * <p>Every method throws {@link AiUnavailableException} when the service cannot answer.
 */
public interface AiAdvisorPort {
    AssignmentPlan assignRooms(List<CourseDemand> courses, List<RoomOption> rooms, double safetyMargin);

    List<SensorAnomaly> detectAnomalies(List<Telemetry> readings);

    Forecast forecastOccupancy(String spaceId, int capacity, List<OccupancySample> history, int horizonHours,
                               String timeZone);

    record CourseDemand(String courseId, int attendance) {
    }

    record RoomOption(String roomId, int capacity, double powerKw) {
    }

    record Assignment(String courseId, String roomId, int attendance, int capacity, double utilizationPct,
                      double nominalPowerKw) {
    }

    /** {@code reason} explains why the plan is not feasible; {@code totalPowerKw} is {@code null} then. */
    record AssignmentPlan(boolean feasible, List<Assignment> assignments, Double totalPowerKw, String reason) {
        public AssignmentPlan {
            assignments = List.copyOf(assignments);
        }
    }

    /** {@code kind}: FUERA_DE_RANGO, VALOR_PEGADO or SALTO. */
    record SensorAnomaly(String spaceId, String sensor, Instant at, String kind, double value, String detail) {
    }

    record ForecastPoint(Instant at, double people, double low, double high) {
    }

    record Forecast(String spaceId, String method, int samplesUsed, List<ForecastPoint> points, String note) {
        public Forecast {
            points = List.copyOf(points);
        }
    }

    /** The AI service answered 4xx: it is up, but refused the request (a contract mismatch between services). */
    final class AiRejectedRequestException extends RuntimeException {
        public AiRejectedRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The AI service is unreachable, overloaded or answered outside its contract. */
    final class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
