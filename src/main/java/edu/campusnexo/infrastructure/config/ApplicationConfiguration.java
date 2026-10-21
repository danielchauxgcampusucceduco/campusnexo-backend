package edu.campusnexo.infrastructure.config;

import edu.campusnexo.application.alert.AirQualityAlertObserver;
import edu.campusnexo.application.alert.AlertQueryService;
import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.alert.AlertThresholds;
import edu.campusnexo.application.alert.CapacityAlertObserver;
import edu.campusnexo.application.alert.EnergyWasteAlertObserver;
import edu.campusnexo.application.alert.ReservationNoShowObserver;
import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.climate.ClimateRecommendationService;
import edu.campusnexo.application.climate.HvacStateResolver;
import edu.campusnexo.application.dashboard.DashboardSummaryService;
import edu.campusnexo.application.dashboard.EmptySpaceEnergyEstimator;
import edu.campusnexo.application.occupancy.FallbackOccupancyEstimator;
import edu.campusnexo.application.occupancy.LiveOccupancyService;
import edu.campusnexo.application.occupancy.LocalHeuristicOccupancyEstimator;
import edu.campusnexo.application.occupancy.OccupancyEstimationService;
import edu.campusnexo.application.occupancy.RetryingOccupancyEstimator;
import edu.campusnexo.application.operations.CampusOperationsFacade;
import edu.campusnexo.application.port.in.AlertQueryUseCase;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ClimateRecommendationUseCase;
import edu.campusnexo.application.port.in.DashboardQueryUseCase;
import edu.campusnexo.application.port.in.LiveOccupancyUseCase;
import edu.campusnexo.application.port.in.OccupancyEstimationUseCase;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.SpaceEvaluationUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.reservation.ReservationService;
import edu.campusnexo.application.telemetry.ObserverFailureReporter;
import edu.campusnexo.application.telemetry.TelemetryEventPublisher;
import edu.campusnexo.application.telemetry.TelemetryObserver;
import edu.campusnexo.application.telemetry.TelemetryService;
import edu.campusnexo.domain.climate.policy.AndeanClimatePolicyFactory;
import edu.campusnexo.domain.climate.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.climate.policy.MildClimatePolicyFactory;
import edu.campusnexo.domain.climate.policy.TropicalClimatePolicyFactory;
import edu.campusnexo.domain.port.AlertRepository;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import edu.campusnexo.domain.port.OccupancyEstimateRepository;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import edu.campusnexo.domain.port.TelemetryRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Composition root: the only place that knows the concrete classes of the Spring-free core.
 * Use cases are exposed through their input-port interfaces.
 */
@Configuration
public class ApplicationConfiguration {

    // ---- Catalog and telemetry -------------------------------------------------------------

    @Bean
    CampusCatalogUseCase campusCatalog(SpaceCatalogRepository repository) {
        return new SpaceCatalogService(repository);
    }

    /**
     * Observers run off the request thread on a small bounded pool. When the queue is full the caller runs
     * the delivery itself: ingestion slows down instead of dropping reactions.
     */
    @Bean
    ObserverPool telemetryObserverPool(@Value("${app.telemetry.observers.threads}") int threads,
                                       @Value("${app.telemetry.observers.queue-capacity}") int queueCapacity) {
        AtomicInteger number = new AtomicInteger();
        return new ObserverPool(new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "telemetry-observer-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.CallerRunsPolicy()));
    }

    @Bean
    TelemetryEventPublisher telemetryEventPublisher(List<TelemetryObserver> observers,
                                                    ObserverFailureReporter failureReporter, ObserverPool pool,
                                                    @Value("${app.telemetry.observers.max-attempts}") int maxAttempts,
                                                    @Value("${app.telemetry.observers.backoff}") Duration backoff) {
        return new TelemetryEventPublisher(observers, failureReporter, pool.executor(), maxAttempts, backoff);
    }

    /**
     * Holder of the observers' thread pool. Deliberately not an {@code Executor} bean, so Spring Boot keeps
     * its own task executor for MVC and messaging; closed (pool shut down) with the context.
     */
    record ObserverPool(ThreadPoolExecutor executor) implements AutoCloseable {
        @Override
        public void close() {
            executor.shutdown();
        }
    }

    @Bean
    TelemetryUseCase telemetry(Clock clock, TelemetryRepository repository, CampusCatalogUseCase catalog,
                               TelemetryEventPublisher publisher,
                               @Value("${app.telemetry.max-future-skew}") Duration maxFutureSkew,
                               @Value("${app.telemetry.max-age}") Duration maxAge) {
        return new TelemetryService(clock, repository, catalog, publisher, maxFutureSkew, maxAge);
    }

    // ---- Alerts (Observer) ----------------------------------------------------------------

    @Bean
    AlertThresholds alertThresholds(@Value("${app.alerts.co2-threshold-ppm}") double co2Ppm,
                                    @Value("${app.alerts.empty-space-power-threshold-kw}") double emptySpacePowerKw,
                                    @Value("${app.alerts.cooldown}") Duration cooldown,
                                    @Value("${app.alerts.reservation-grace}") Duration reservationGrace) {
        return new AlertThresholds(co2Ppm, emptySpacePowerKw, cooldown, reservationGrace);
    }

    @Bean
    AlertRecorder alertRecorder(AlertRepository repository) {
        return new AlertRecorder(repository);
    }

    @Bean
    TelemetryObserver airQualityAlertObserver(AlertRecorder recorder, AlertThresholds thresholds) {
        return new AirQualityAlertObserver(recorder, thresholds);
    }

    @Bean
    TelemetryObserver capacityAlertObserver(AlertRecorder recorder, AlertThresholds thresholds) {
        return new CapacityAlertObserver(recorder, thresholds);
    }

    @Bean
    TelemetryObserver energyWasteAlertObserver(AlertRecorder recorder, AlertThresholds thresholds) {
        return new EnergyWasteAlertObserver(recorder, thresholds);
    }

    @Bean
    TelemetryObserver reservationNoShowObserver(AlertRecorder recorder, ReservationRepository reservations,
                                                AlertThresholds thresholds) {
        return new ReservationNoShowObserver(recorder, reservations, thresholds);
    }

    @Bean
    AlertQueryUseCase alertQuery(AlertRepository repository, CampusCatalogUseCase catalog) {
        return new AlertQueryService(repository, catalog);
    }

    // ---- Occupancy (Adapter + Decorator) ---------------------------------------------------

    /** HTTP adapter wrapped by retry and fallback decorators. */
    @Bean
    @Primary
    OccupancyEstimator occupancyEstimator(@Qualifier("httpOccupancyEstimatorAdapter") OccupancyEstimator remote,
                                          @Value("${app.ai.max-attempts}") int maxAttempts,
                                          @Value("${app.ai.fallback-enabled}") boolean fallbackEnabled) {
        OccupancyEstimator estimator = new RetryingOccupancyEstimator(remote, maxAttempts);
        return fallbackEnabled
                ? new FallbackOccupancyEstimator(estimator, new LocalHeuristicOccupancyEstimator())
                : estimator;
    }

    @Bean
    OccupancyEstimationUseCase occupancyEstimation(Clock clock, OccupancyEstimator estimator,
                                                   OccupancyEstimateRepository repository,
                                                   CampusCatalogUseCase catalog,
                                                   @Value("${app.ai.default-outdoor-co2-ppm}") double outdoorCo2Ppm) {
        return new OccupancyEstimationService(clock, estimator, repository, catalog, outdoorCo2Ppm);
    }

    @Bean
    LiveOccupancyUseCase liveOccupancy(Clock clock, CampusCatalogUseCase catalog, TelemetryRepository telemetry,
                                       @Value("${app.occupancy.stale-after}") Duration staleAfter) {
        return new LiveOccupancyService(clock, catalog, telemetry, staleAfter);
    }

    // ---- Climate (Abstract Factory + Strategy + Builder) -----------------------------------

    @Bean
    ClimatePolicyFactory andeanClimatePolicyFactory() {
        return new AndeanClimatePolicyFactory();
    }

    @Bean
    ClimatePolicyFactory tropicalClimatePolicyFactory() {
        return new TropicalClimatePolicyFactory();
    }

    @Bean
    ClimatePolicyFactory mildClimatePolicyFactory() {
        return new MildClimatePolicyFactory();
    }

    @Bean
    ClimateRecommendationUseCase climateRecommendations(Clock clock, CampusCatalogUseCase catalog,
                                                        ClimateRecommendationRepository repository,
                                                        List<ClimatePolicyFactory> factories) {
        return new ClimateRecommendationService(clock, catalog, repository,
                new HvacStateResolver(repository, clock), factories);
    }

    // ---- Reservations, dashboard and Facade ------------------------------------------------

    @Bean
    ReservationUseCase reservations(Clock clock, ReservationRepository repository, CampusCatalogUseCase catalog) {
        return new ReservationService(clock, repository, catalog);
    }

    @Bean
    DashboardQueryUseCase dashboard(Clock clock, TelemetryRepository telemetry, AlertRepository alerts,
                                    CampusCatalogUseCase catalog, LiveOccupancyUseCase liveOccupancy,
                                    @Value("${app.dashboard.max-reading-interval}") Duration maxReadingInterval) {
        return new DashboardSummaryService(clock, telemetry, alerts, catalog, liveOccupancy,
                new EmptySpaceEnergyEstimator(maxReadingInterval));
    }

    @Bean
    SpaceEvaluationUseCase spaceEvaluation(CampusCatalogUseCase catalog, TelemetryUseCase telemetry,
                                           OccupancyEstimationUseCase occupancy,
                                           ClimateRecommendationUseCase climate,
                                           ReservationUseCase reservations, AlertQueryUseCase alerts) {
        return new CampusOperationsFacade(catalog, telemetry, occupancy, climate, reservations, alerts);
    }
}
