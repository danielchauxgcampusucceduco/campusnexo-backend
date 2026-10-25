package edu.campusnexo.infrastructure.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import edu.campusnexo.application.port.out.AiAdvisorPort;
import edu.campusnexo.application.port.out.TwinHistoryPort.OccupancySample;
import edu.campusnexo.support.TestData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpAiAdvisorAdapterTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpAiAdvisorAdapter adapter = new HttpAiAdvisorAdapter(builder.baseUrl("http://ai.test").build());

    @Test
    void sendsCoursesAndRoomsAndMapsThePlan() {
        server.expect(requestTo("http://ai.test/v1/rooms/assignment"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"courses":[{"course_id":"IS-701","attendance":25}],
                         "rooms":[{"room_id":"301-A","capacity":30,"power_kw":7.2}],"safety_margin":1.1}
                        """))
                .andRespond(withSuccess("""
                        {"feasible":true,"assignments":[{"course_id":"IS-701","room_id":"301-A","attendance":25,
                         "capacity":30,"utilization_pct":83.3,"nominal_power_kw":7.2}],"total_nominal_power_kw":7.2}
                        """, MediaType.APPLICATION_JSON));

        AiAdvisorPort.AssignmentPlan plan = adapter.assignRooms(List.of(new AiAdvisorPort.CourseDemand("IS-701", 25)),
                List.of(new AiAdvisorPort.RoomOption("301-A", 30, 7.2)), 1.1);

        server.verify();
        assertTrue(plan.feasible());
        assertEquals("301-A", plan.assignments().getFirst().roomId());
        assertEquals(7.2, plan.totalPowerKw());
    }

    @Test
    void sendsReadingsAndMapsAnomalies() {
        server.expect(requestTo("http://ai.test/v1/sensors/anomalies"))
                .andExpect(content().json("""
                        {"readings":[{"space_id":"301-A","co2_ppm":9000.0,"temperature_c":22.0}]}
                        """))
                .andRespond(withSuccess("""
                        {"anomalies":[{"space_id":"301-A","sensor":"co2_ppm","timestamp":"2026-10-24T13:59:00Z",
                         "kind":"FUERA_DE_RANGO","value":9000.0,"detail":"fuera de 300–5000 ppm"}],"readings_analyzed":1}
                        """, MediaType.APPLICATION_JSON));

        List<AiAdvisorPort.SensorAnomaly> anomalies = adapter.detectAnomalies(
                List.of(TestData.reading("301-A", TestData.NOW.minusSeconds(60), 10, 9_000, 1.0)));

        assertEquals("FUERA_DE_RANGO", anomalies.getFirst().kind());
        assertEquals(Instant.parse("2026-10-24T13:59:00Z"), anomalies.getFirst().at());
    }

    @Test
    void sendsHistoryAndRejectsAForecastOfAnotherRoom() {
        OccupancySample sample = new OccupancySample(TestData.NOW, "301-A", 12, 12, 1, 1, 11, 12, 800, true, true, List.of());
        server.expect(requestTo("http://ai.test/v1/occupancy/forecast"))
                .andExpect(content().json("""
                        {"space_id":"301-A","capacity":30,"timezone":"America/Bogota","horizon_hours":6,
                         "history":[{"people":12}]}
                        """))
                .andRespond(withSuccess("""
                        {"space_id":"303-A","method":"perfil_semanal","samples_used":1,"points":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThrows(AiAdvisorPort.AiUnavailableException.class,
                () -> adapter.forecastOccupancy("301-A", 30, List.of(sample), 6, "America/Bogota"));
    }

    @Test
    void anErrorStatusMeansTheServiceIsUnavailable() {
        server.expect(requestTo("http://ai.test/v1/rooms/assignment"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        AiAdvisorPort.AiUnavailableException failure = assertThrows(AiAdvisorPort.AiUnavailableException.class,
                () -> adapter.assignRooms(List.of(), List.of(), 1.1));
        assertFalse(failure.getMessage().isBlank());
    }

    @Test
    void talksHttp11SoUvicornReceivesTheBody() {
        assertEquals(java.net.http.HttpClient.Version.HTTP_1_1,
                HttpOccupancyEstimatorAdapter.jdkClient(java.time.Duration.ofSeconds(1)).version());
    }

    @Test
    void aRejectedRequestIsNotReportedAsAnOutage() {
        server.expect(requestTo("http://ai.test/v1/occupancy/forecast"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).body("{\"detail\":\"history\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(AiAdvisorPort.AiRejectedRequestException.class, () -> adapter.forecastOccupancy("301-A", 30,
                List.of(new OccupancySample(TestData.NOW, "301-A", 5, 5, 1, 1, 4, 5, 600, true, false, List.of())), 12,
                "America/Bogota"));
    }
}
