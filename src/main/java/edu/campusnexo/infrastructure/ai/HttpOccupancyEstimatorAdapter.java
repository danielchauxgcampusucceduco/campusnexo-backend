package edu.campusnexo.infrastructure.ai;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter: translates the Java {@link OccupancyEstimator} contract into the FastAPI endpoint
 * (snake_case JSON) and the answer back into the domain model. Transport failures become
 * "unavailable"; answers that break the contract become "invalid response".
 */
@Component
class HttpOccupancyEstimatorAdapter implements OccupancyEstimator {
    private final RestClient client;

    HttpOccupancyEstimatorAdapter(RestClient.Builder builder,
                                  @Value("${app.ai.base-url}") String baseUrl,
                                  @Value("${app.ai.connect-timeout}") Duration connectTimeout,
                                  @Value("${app.ai.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        EstimateResponse response;
        try {
            response = client.post()
                    .uri("/v1/occupancy/estimate")
                    .body(new EstimateRequest(query.spaceId(), query.cameraPeople(), query.co2Ppm(),
                            query.wifiDevices(), query.outdoorCo2Ppm()))
                    .retrieve()
                    .body(EstimateResponse.class);
        } catch (ResourceAccessException | HttpServerErrorException failure) {
            throw new OccupancyEstimatorUnavailableException(failure);
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

    private record EstimateRequest(
            @JsonProperty("space_id") String spaceId,
            @JsonProperty("yolo_people") Integer yoloPeople,
            @JsonProperty("co2_ppm") double co2Ppm,
            @JsonProperty("wifi_devices") int wifiDevices,
            @JsonProperty("outdoor_co2_ppm") double outdoorCo2Ppm) {
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
