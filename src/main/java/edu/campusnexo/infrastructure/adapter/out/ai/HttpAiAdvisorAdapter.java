package edu.campusnexo.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.campusnexo.application.port.out.AiAdvisorPort;
import edu.campusnexo.application.port.out.TwinHistoryPort.OccupancySample;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Adapter of the AI service's administration tools: translates {@link AiAdvisorPort} into the FastAPI
 * endpoints (snake_case JSON). Any transport failure, error status or answer outside the contract becomes
 * {@link AiUnavailableException}; these tools are asked on demand by an administrator, so there is no retry.
 */
@Component
class HttpAiAdvisorAdapter implements AiAdvisorPort {
    private final RestClient client;

    @Autowired
    HttpAiAdvisorAdapter(RestClient.Builder builder,
                         @Value("${app.ai.base-url}") String baseUrl,
                         @Value("${app.ai.connect-timeout}") Duration connectTimeout,
                         @Value("${app.ai.tools-read-timeout}") Duration readTimeout,
                         @Value("${app.ai.api-key:}") String apiKey) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpOccupancyEstimatorAdapter.jdkClient(connectTimeout));
        requestFactory.setReadTimeout(readTimeout);
        this.client = HttpOccupancyEstimatorAdapter.withApiKey(
                builder.clone().baseUrl(baseUrl).requestFactory(requestFactory), apiKey).build();
    }

    HttpAiAdvisorAdapter(RestClient client) {
        this.client = client;
    }

    @Override
    public AssignmentPlan assignRooms(List<CourseDemand> courses, List<RoomOption> rooms, double safetyMargin) {
        AssignmentResponse response = post("/v1/rooms/assignment", new AssignmentRequest(
                courses.stream().map(c -> new CourseJson(c.courseId(), c.attendance())).toList(),
                rooms.stream().map(r -> new RoomJson(r.roomId(), r.capacity(), r.powerKw())).toList(),
                safetyMargin), AssignmentResponse.class);
        if (response.feasible() == null || response.assignments() == null) {
            throw invalid("asignación sin feasible o assignments");
        }
        return new AssignmentPlan(response.feasible(), response.assignments().stream()
                .map(a -> new Assignment(a.courseId(), a.roomId(), a.attendance(), a.capacity(), a.utilizationPct(),
                        a.nominalPowerKw()))
                .toList(), response.totalNominalPowerKw(), response.reason());
    }

    @Override
    public List<SensorAnomaly> detectAnomalies(List<Telemetry> readings) {
        AnomaliesResponse response = post("/v1/sensors/anomalies", new AnomaliesRequest(readings.stream()
                .map(r -> new ReadingJson(r.spaceId(), r.recordedAt(), r.co2Ppm(), r.temperatureC(), r.humidityPct(),
                        r.illuminanceLux(), r.activePowerKw()))
                .toList()), AnomaliesResponse.class);
        if (response.anomalies() == null) {
            throw invalid("anomalías sin la lista anomalies");
        }
        return response.anomalies().stream()
                .map(a -> new SensorAnomaly(a.spaceId(), a.sensor(), a.timestamp(), a.kind(), a.value(), a.detail()))
                .toList();
    }

    @Override
    public Forecast forecastOccupancy(String spaceId, int capacity, List<OccupancySample> history, int horizonHours,
                                      String timeZone) {
        ForecastResponse response = post("/v1/occupancy/forecast", new ForecastRequest(spaceId, capacity, timeZone,
                horizonHours, history.stream().map(s -> new SampleJson(s.at(), s.people())).toList()),
                ForecastResponse.class);
        if (response.points() == null || !spaceId.equals(response.spaceId())) {
            throw invalid("pronóstico sin points o de otro espacio");
        }
        return new Forecast(spaceId, response.method(), response.samplesUsed(), response.points().stream()
                .map(p -> new ForecastPoint(p.timestamp(), p.people(), p.low(), p.high())).toList(), response.note());
    }

    private <T> T post(String path, Object body, Class<T> type) {
        T response;
        try {
            response = client.post().uri(path).body(body).retrieve().body(type);
        } catch (HttpClientErrorException rejected) {
            // A 4xx means the service is up and refused this request: a contract problem, not an outage.
            throw new AiAdvisorPort.AiRejectedRequestException("El servicio de IA rechazó la petición a " + path
                    + " (HTTP " + rejected.getStatusCode().value() + "): " + rejected.getResponseBodyAsString(), rejected);
        } catch (RestClientException failure) {
            throw new AiUnavailableException("El servicio de IA no respondió a " + path + ": " + failure.getMessage(),
                    failure);
        }
        if (response == null) {
            throw invalid("respuesta vacía de " + path);
        }
        return response;
    }

    private static AiUnavailableException invalid(String detail) {
        return new AiUnavailableException("Respuesta no válida del servicio de IA: " + detail, null);
    }

    private record CourseJson(@JsonProperty("course_id") String courseId, int attendance) {
    }

    private record RoomJson(@JsonProperty("room_id") String roomId, int capacity,
                            @JsonProperty("power_kw") double powerKw) {
    }

    private record AssignmentRequest(List<CourseJson> courses, List<RoomJson> rooms,
                                     @JsonProperty("safety_margin") double safetyMargin) {
    }

    private record AssignmentJson(@JsonProperty("course_id") String courseId, @JsonProperty("room_id") String roomId,
                                  int attendance, int capacity,
                                  @JsonProperty("utilization_pct") double utilizationPct,
                                  @JsonProperty("nominal_power_kw") double nominalPowerKw) {
    }

    private record AssignmentResponse(Boolean feasible, List<AssignmentJson> assignments,
                                      @JsonProperty("total_nominal_power_kw") Double totalNominalPowerKw,
                                      String reason) {
    }

    private record ReadingJson(@JsonProperty("space_id") String spaceId, Instant timestamp,
                               @JsonProperty("co2_ppm") double co2Ppm,
                               @JsonProperty("temperature_c") double temperatureC,
                               @JsonProperty("humidity_pct") double humidityPct,
                               @JsonProperty("illuminance_lux") double illuminanceLux,
                               @JsonProperty("active_power_kw") double activePowerKw) {
    }

    private record AnomaliesRequest(List<ReadingJson> readings) {
    }

    private record AnomalyJson(@JsonProperty("space_id") String spaceId, String sensor, Instant timestamp, String kind,
                               double value, String detail) {
    }

    private record AnomaliesResponse(List<AnomalyJson> anomalies) {
    }

    private record SampleJson(Instant timestamp, int people) {
    }

    private record ForecastRequest(@JsonProperty("space_id") String spaceId, int capacity, String timezone,
                                   @JsonProperty("horizon_hours") int horizonHours, List<SampleJson> history) {
    }

    private record PointJson(Instant timestamp, double people, double low, double high) {
    }

    private record ForecastResponse(@JsonProperty("space_id") String spaceId, String method,
                                    @JsonProperty("samples_used") int samplesUsed, List<PointJson> points,
                                    String note) {
    }
}
