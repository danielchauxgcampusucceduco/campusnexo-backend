package edu.campusnexo.infrastructure.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorInvalidResponseException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpOccupancyEstimatorAdapterTest {
    @Test
    void sendsThePythonContractAndMapsTheValidatedEstimate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/v1/occupancy/estimate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"space_id":"A-301","yolo_people":12,"co2_ppm":720.0,"wifi_devices":10,"outdoor_co2_ppm":420.0}
                        """))
                .andRespond(withSuccess("""
                        {"space_id":"A-301","occupancy_estimate":14,"confidence_pct":82.5,
                         "camera_available":true,"is_estimate":true,"note":"estimación ilustrativa"}
                        """, MediaType.APPLICATION_JSON));
        HttpOccupancyEstimatorAdapter adapter = new HttpOccupancyEstimatorAdapter(
                builder.baseUrl("http://ai.test").build());

        var result = adapter.estimate(new OccupancyQuery("A-301", 32, 48, 12, 720, 10, 420));

        server.verify();
        assertEquals("A-301", result.spaceId());
        assertEquals(14, result.occupancyEstimate());
        assertEquals(82.5, result.confidencePct());
        assertEquals(OccupancySource.SERVICIO_IA, result.source());
        assertTrue(result.cameraAvailable());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"space_id\":\"A-301\",\"occupancy_estimate\":null,\"confidence_pct\":80}",
            "{\"space_id\":\"B-201\",\"occupancy_estimate\":14,\"confidence_pct\":80}",
            "{\"space_id\":\"A-301\",\"occupancy_estimate\":14,\"confidence_pct\":101}"
    })
    void classifiesMissingMismatchedAndOutOfRangeResponsesAsInvalid(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/v1/occupancy/estimate"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        HttpOccupancyEstimatorAdapter adapter = new HttpOccupancyEstimatorAdapter(
                builder.baseUrl("http://ai.test").build());

        org.junit.jupiter.api.Assertions.assertThrows(OccupancyEstimatorInvalidResponseException.class,
                () -> adapter.estimate(new OccupancyQuery("A-301", 32, 48, null, 720, 10, 420)));
        server.verify();
    }

    @Test
    void mapsAnUpstreamServerErrorToServiceUnavailableForRetryAndFallback() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/v1/occupancy/estimate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        HttpOccupancyEstimatorAdapter adapter = new HttpOccupancyEstimatorAdapter(
                builder.baseUrl("http://ai.test").build());

        org.junit.jupiter.api.Assertions.assertThrows(OccupancyEstimatorUnavailableException.class,
                () -> adapter.estimate(new OccupancyQuery("A-301", 32, 48, null, 720, 10, 420)));
        server.verify();
    }
}
