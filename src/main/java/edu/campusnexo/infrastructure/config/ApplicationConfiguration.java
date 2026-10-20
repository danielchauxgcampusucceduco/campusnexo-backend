package edu.campusnexo.infrastructure.config;

import edu.campusnexo.application.DashboardSummaryService;
import edu.campusnexo.application.OccupancyEstimationService;
import edu.campusnexo.application.TelemetryService;
import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.climate.AndeanClimatePolicyFactory;
import edu.campusnexo.application.climate.ClimateRecommendationService;
import edu.campusnexo.application.climate.MildClimatePolicyFactory;
import edu.campusnexo.application.climate.TropicalClimatePolicyFactory;
import edu.campusnexo.application.event.TelemetryEventPublisher;
import edu.campusnexo.application.event.TelemetryObserver;
import edu.campusnexo.application.event.ObserverFailureReporter;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.policy.ClimatePolicyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/** Wires application use cases to ports while keeping Spring outside the application core. */
@Configuration
public class ApplicationConfiguration {
    @Bean
    SpaceCatalogService spaceCatalogService(SpaceCatalogRepository repository) {
        return new SpaceCatalogService(repository);
    }

    @Bean
    OccupancyEstimationService occupancyEstimationService(OccupancyEstimator estimator, CampusCatalogUseCase spaces) {
        return new OccupancyEstimationService(estimator, spaces);
    }

    @Bean
    TelemetryEventPublisher telemetryEventPublisher(List<TelemetryObserver> observers,
            ObserverFailureReporter failureReporter) {
        return new TelemetryEventPublisher(observers, failureReporter);
    }

    @Bean
    TelemetryService telemetryService(TelemetryRepository telemetry, CampusCatalogUseCase spaces,
                                      TelemetryEventPublisher publisher) {
        return new TelemetryService(telemetry, spaces, publisher);
    }

    @Bean
    ClimateRecommendationService climateRecommendationService(Clock clock, CampusCatalogUseCase spaces,
            ClimateRecommendationRepository recommendations, List<ClimatePolicyFactory> factories) {
        return new ClimateRecommendationService(clock, spaces, recommendations, factories);
    }

    @Bean
    DashboardSummaryService dashboardSummaryService(Clock clock, TelemetryRepository telemetry) {
        return new DashboardSummaryService(clock, telemetry);
    }

    @Bean
    ClimatePolicyFactory andeanClimatePolicyFactory() { return new AndeanClimatePolicyFactory(); }

    @Bean
    ClimatePolicyFactory tropicalClimatePolicyFactory() { return new TropicalClimatePolicyFactory(); }

    @Bean
    ClimatePolicyFactory mildClimatePolicyFactory() { return new MildClimatePolicyFactory(); }
}
