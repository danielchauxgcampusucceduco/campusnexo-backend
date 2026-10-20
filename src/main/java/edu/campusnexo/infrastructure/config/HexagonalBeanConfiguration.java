package edu.campusnexo.infrastructure.config;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.application.simulation.SimulationService;
import edu.campusnexo.application.twin.ProcessSpaceTelemetryService;
import edu.campusnexo.application.twin.RecentTraceBuffer;
import edu.campusnexo.application.twin.TwinControlService;
import edu.campusnexo.application.twin.TwinQueryService;
import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.port.RealtimeDashboardNotifierPort;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.KalmanParameters;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import edu.campusnexo.domain.twin.SpaceStatusPolicy;
import edu.campusnexo.domain.twin.ThermalInertiaMpcDomainService;
import edu.campusnexo.domain.twin.ThermalParameters;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the digital twin. Domain services are plain Java objects built here from
 * {@link TwinProperties}; the domain never sees Spring. {@link ProcessSpaceTelemetryService} is one bean
 * exposed through its two input ports (telemetry processing and re-evaluation).
 */
@Configuration
@EnableConfigurationProperties(TwinProperties.class)
class HexagonalBeanConfiguration {

    @Bean
    OccupancyKalmanDomainService occupancyKalmanDomainService(TwinProperties properties) {
        TwinProperties.Kalman k = properties.kalman();
        return new OccupancyKalmanDomainService(new KalmanParameters(k.processNoisePerMinute(), k.cameraVariance(),
                k.co2RelativeError(), k.wifiRelativeError(), k.peoplePerDevice(), k.outdoorCo2Ppm(), k.co2PpmPerPersonSqm()));
    }

    @Bean
    ThermalInertiaMpcDomainService thermalInertiaMpcDomainService(TwinProperties properties) {
        TwinProperties.Thermal t = properties.thermal();
        return new ThermalInertiaMpcDomainService(new ThermalParameters(t.timeConstantMinutes(), t.occupantGainCPerHour(),
                t.hvacRateCPerMinute(), t.stepMinutes(), t.defaultOccupiedMinutes(), t.preConditioningLeadMinutes(),
                t.ecoSetbackWindowMinutes(), t.ecoSetbackOffsetC(), t.co2VentilationPpm()));
    }

    @Bean
    SpaceStatusPolicy spaceStatusPolicy(TwinProperties properties) {
        return new SpaceStatusPolicy(properties.status().noShowGrace(), properties.status().underuseRatio());
    }

    @Bean
    EnergyAccountingPolicy energyAccountingPolicy(TwinProperties properties) {
        TwinProperties.Energy e = properties.energy();
        return new EnergyAccountingPolicy(e.ratedKwPerSqm(), e.maxReadingInterval(), LocalTime.of(e.scheduleStartHour(), 0),
                LocalTime.of(e.scheduleEndHour(), 0), ZoneId.of(e.timeZone()));
    }

    @Bean
    RecentTraceBuffer recentTraceBuffer(TwinProperties properties) {
        return new RecentTraceBuffer(properties.traceHistory());
    }

    @Bean
    ProcessSpaceTelemetryService processSpaceTelemetryService(Clock clock, CampusCatalogUseCase catalog,
                                                              TelemetryUseCase telemetry, ReservationRepository reservations,
                                                              SpaceRepositoryPort twins, HvacActuatorCommandPort actuator,
                                                              RealtimeDashboardNotifierPort notifier,
                                                              OccupancyKalmanDomainService kalman,
                                                              ThermalInertiaMpcDomainService thermal,
                                                              SpaceStatusPolicy statusPolicy, EnergyAccountingPolicy energy,
                                                              RecentTraceBuffer traces, TwinProperties properties) {
        return new ProcessSpaceTelemetryService(clock, catalog, telemetry, reservations, twins, actuator, notifier, kalman,
                thermal, statusPolicy, energy, properties.status().deskAbandonThreshold(), traces);
    }

    @Bean
    TwinControlUseCase twinControl(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                                   ProcessSpaceTelemetryService reevaluation) {
        return new TwinControlService(clock, catalog, twins, reevaluation);
    }

    @Bean
    TwinQueryUseCase twinQuery(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                               ReservationRepository reservations, EnergyAccountingPolicy energy, RecentTraceBuffer traces,
                               TwinProperties properties) {
        return new TwinQueryService(clock, catalog, twins, reservations, energy,
                properties.energy().emissionFactorKgCo2PerKwh(), traces);
    }

    @Bean
    SimulationUseCase simulation(Clock clock, ProcessSpaceTelemetryService pipeline, TwinControlUseCase controls,
                                 ReservationUseCase reservations, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                                 TwinQueryUseCase queries) {
        return new SimulationService(clock, pipeline, controls, reservations, catalog, twins, queries);
    }
}
