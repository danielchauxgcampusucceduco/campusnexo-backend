package edu.campusnexo.application.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.application.alert.AlertQueryService;
import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.climate.ClimateRecommendationService;
import edu.campusnexo.application.climate.HvacStateResolver;
import edu.campusnexo.application.exception.NoTelemetryException;
import edu.campusnexo.application.occupancy.FallbackOccupancyEstimator;
import edu.campusnexo.application.occupancy.LocalHeuristicOccupancyEstimator;
import edu.campusnexo.application.occupancy.OccupancyEstimationService;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SpaceEvaluation;
import edu.campusnexo.application.reservation.ReservationService;
import edu.campusnexo.application.telemetry.TelemetryEventPublisher;
import edu.campusnexo.application.telemetry.TelemetryService;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.policy.AndeanClimatePolicyFactory;
import edu.campusnexo.domain.climate.policy.MildClimatePolicyFactory;
import edu.campusnexo.domain.climate.policy.TropicalClimatePolicyFactory;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.InMemoryClimateRecommendationRepository;
import edu.campusnexo.support.InMemoryOccupancyEstimateRepository;
import edu.campusnexo.support.InMemoryPersonaRepository;
import edu.campusnexo.support.InMemoryReservationRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.support.InMemoryTelemetryRepository;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CampusOperationsFacadeTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final InMemoryTelemetryRepository telemetryRepository = new InMemoryTelemetryRepository();
    private final InMemoryOccupancyEstimateRepository estimates = new InMemoryOccupancyEstimateRepository();
    private final InMemoryClimateRecommendationRepository recommendations = new InMemoryClimateRecommendationRepository();
    private final CampusCatalogUseCase catalog = new SpaceCatalogService(
            new InMemorySpaceCatalogRepository(TestData.andeanClassroom()));

    private CampusOperationsFacade facade(OccupancyEstimator estimator) {
        InMemoryReservationRepository reservations = new InMemoryReservationRepository();
        return new CampusOperationsFacade(catalog,
                new TelemetryService(clock, telemetryRepository, catalog,
                        new TelemetryEventPublisher(List.of(), (observer, event, failure) -> { }),
                        Duration.ofMinutes(5), Duration.ofDays(30)),
                new OccupancyEstimationService(clock, estimator, estimates, catalog, 420),
                new ClimateRecommendationService(clock, catalog, recommendations,
                        new HvacStateResolver(new InMemorySpaceRepositoryPort(), clock),
                        List.of(new AndeanClimatePolicyFactory(), new TropicalClimatePolicyFactory(),
                                new MildClimatePolicyFactory())),
                new ReservationService(clock, reservations, catalog, new InMemoryPersonaRepository()),
                new AlertQueryService(new InMemoryAlertRepository(), catalog));
    }

    @Test
    @DisplayName("Evalúa el espacio desde la última lectura: IA (con respaldo) → recomendación trazable")
    void evaluatesFromLatestTelemetry() {
        telemetryRepository.save(new Telemetry("AULA-301", TestData.NOW.minusSeconds(60), 22, 0, 24, 900, 17.0, 60, 250, 2.1,
                22));
        OccupancyEstimator aiDown = query -> {
            throw new OccupancyEstimatorUnavailableException(new RuntimeException("timeout"));
        };

        SpaceEvaluation evaluation = facade(new FallbackOccupancyEstimator(aiDown, new LocalHeuristicOccupancyEstimator()))
                .evaluate("AULA-301");

        assertEquals(OccupancySource.RESPALDO_LOCAL, evaluation.occupancy().estimate().source());
        assertEquals(22, evaluation.occupancy().estimate().occupancyEstimate());
        assertEquals(HvacMode.CALEFACCION, evaluation.recommendation().hvac().mode());
        assertEquals(OccupancySource.RESPALDO_LOCAL, evaluation.recommendation().occupancySource());
        assertEquals(TestData.NOW.minusSeconds(60), evaluation.recommendation().sourceTelemetryAt());
        assertNotNull(evaluation.recommendation().inputs());
        assertEquals(1, estimates.rows().size());
    }

    @Test
    @DisplayName("Sin telemetría responde con un conflicto explícito")
    void requiresTelemetry() {
        assertThrows(NoTelemetryException.class,
                () -> facade(new LocalHeuristicOccupancyEstimator()).evaluate("AULA-301"));
    }
}
