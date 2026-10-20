package edu.campusnexo.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.campusnexo.application.AiServiceUnavailableException;
import edu.campusnexo.domain.model.OccupancyEstimate;
import edu.campusnexo.domain.port.OccupancyEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class HttpOccupancyEstimatorAdapter implements OccupancyEstimator {
    private final RestClient client;
    HttpOccupancyEstimatorAdapter(RestClient.Builder builder,
            @Value("${app.ai.base-url:http://localhost:8001}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }
    private record EstimateRequest(@JsonProperty("space_id") String spaceId,
            @JsonProperty("yolo_people") Integer yoloPeople, @JsonProperty("co2_ppm") double co2Ppm,
            @JsonProperty("wifi_devices") int wifiDevices, @JsonProperty("outdoor_co2_ppm") double outdoorCo2Ppm) {}
    private record EstimateResponse(@JsonProperty("space_id") String spaceId,
            @JsonProperty("occupancy_estimate") int occupancyEstimate,
            @JsonProperty("confidence_pct") double confidencePct,
            @JsonProperty("camera_available") boolean cameraAvailable,
            @JsonProperty("is_estimate") boolean isEstimate, String note) {}
    @Override public OccupancyEstimate estimate(String spaceId, Integer yoloPeople, double co2Ppm,
                                                 int wifiDevices, double outdoorCo2Ppm) {
        try {
            EstimateResponse response = client.post().uri("/v1/occupancy/estimate")
                    .body(new EstimateRequest(spaceId, yoloPeople, co2Ppm, wifiDevices, outdoorCo2Ppm))
                    .retrieve().body(EstimateResponse.class);
            if (response == null) throw new AiServiceUnavailableException();
            return new OccupancyEstimate(response.spaceId(), response.occupancyEstimate(),
                    response.confidencePct(), response.cameraAvailable(), response.isEstimate(), response.note());
        } catch (RestClientException ex) {
            throw new AiServiceUnavailableException();
        }
    }
}
