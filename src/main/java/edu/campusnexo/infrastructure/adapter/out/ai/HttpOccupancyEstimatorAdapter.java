package edu.campusnexo.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorInvalidResponseException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adapter: translates the Java {@link OccupancyEstimator} contract into the FastAPI endpoint
 * (snake_case JSON) and the answer back into the domain model. Transport failures, 5xx and 429 become
 * "unavailable" (with the service's {@code Retry-After}, if any, so the circuit breaker honours it); answers
 * that break the contract become "invalid response". Calls carry the service key in {@code X-API-Key}.
 */
@Component
class HttpOccupancyEstimatorAdapter implements OccupancyEstimator {
    private final RestClient client;

    static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    HttpOccupancyEstimatorAdapter(RestClient.Builder builder,
                                  @Value("${app.ai.base-url}") String baseUrl,
                                  @Value("${app.ai.connect-timeout}") Duration connectTimeout,
                                  @Value("${app.ai.read-timeout}") Duration readTimeout,
                                  @Value("${app.ai.api-key:}") String apiKey) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient(connectTimeout));
        requestFactory.setReadTimeout(readTimeout);
        this.client = withApiKey(builder.clone().baseUrl(baseUrl).requestFactory(requestFactory), apiKey).build();
    }

    /**
     * HTTP/1.1 on purpose: over plain http the JDK client would try to upgrade to HTTP/2 (h2c), uvicorn rejects the
     * upgrade and the request body is lost, so every call to the AI service failed.
     */
    static HttpClient jdkClient(Duration connectTimeout) {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build();
    }

    /** Adds the service key to every call; without a key (local development) the header is not sent. */
    static RestClient.Builder withApiKey(RestClient.Builder builder, String apiKey) {
        return apiKey == null || apiKey.isBlank() ? builder : builder.defaultHeader(API_KEY_HEADER, apiKey);
    }

    HttpOccupancyEstimatorAdapter(RestClient client) {
        this.client = client;
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        EstimateResponse response;
        try {
            response = client.post()
                    .uri("/v1/occupancy/estimate")
                    .body(new EstimateRequest(query.spaceId(), query.cameraPeople(), query.co2Ppm(),
                            query.wifiDevices(), query.outdoorCo2Ppm(), query.areaSqm(), query.capacity()))
                    .retrieve()
                    .body(EstimateResponse.class);
        } catch (ResourceAccessException failure) {
            throw new OccupancyEstimatorUnavailableException(failure);
        } catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests failure) {
            throw new OccupancyEstimatorUnavailableException("El servicio de IA no está disponible (HTTP "
                    + failure.getStatusCode().value() + ")", failure, retryAfter(failure));
        } catch (RestClientException failure) {
            throw new OccupancyEstimatorInvalidResponseException("no se pudo interpretar la respuesta", failure);
        }
        if (response == null || response.occupancyEstimate() == null || response.confidencePct() == null) {
            throw new OccupancyEstimatorInvalidResponseException("faltan campos obligatorios", null);
        }
        if (!query.spaceId().equals(response.spaceId())) {
            throw new OccupancyEstimatorInvalidResponseException("el espacio de la respuesta no coincide", null);
        }
        try {
            return new OccupancyEstimate(query.spaceId(), response.occupancyEstimate(), response.confidencePct(),
                    Boolean.TRUE.equals(response.cameraAvailable()), !Boolean.FALSE.equals(response.isEstimate()),
                    OccupancySource.SERVICIO_IA, response.note());
        } catch (DomainValidationException invalid) {
            throw new OccupancyEstimatorInvalidResponseException(invalid.getMessage(), invalid);
        }
    }

    /** {@code Retry-After} in seconds; HTTP dates and garbage are ignored (the breaker's own wait applies). */
    static Duration retryAfter(RestClientResponseException failure) {
        String header = failure.getResponseHeaders() == null ? null
                : failure.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds > 0 ? Duration.ofSeconds(Math.min(seconds, 3_600)) : null;
        } catch (NumberFormatException notSeconds) {
            return null;
        }
    }

    /** Raw measurements of the room; the service fuses them with the same model as the twin. */
    private record EstimateRequest(
            @JsonProperty("space_id") String spaceId,
            @JsonProperty("yolo_people") Integer yoloPeople,
            @JsonProperty("co2_ppm") double co2Ppm,
            @JsonProperty("wifi_devices") int wifiDevices,
            @JsonProperty("outdoor_co2_ppm") double outdoorCo2Ppm,
            @JsonProperty("area_sqm") double areaSqm,
            @JsonProperty("capacity") int capacity) {
    }

    private record EstimateResponse(
            @JsonProperty("space_id") String spaceId,
            @JsonProperty("occupancy_estimate") Integer occupancyEstimate,
            @JsonProperty("confidence_pct") Double confidencePct,
            @JsonProperty("camera_available") Boolean cameraAvailable,
            @JsonProperty("is_estimate") Boolean isEstimate,
            @JsonProperty("note") String note) {
    }
}
