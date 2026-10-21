package edu.campusnexo.infrastructure.config;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.application.simulation.SimulationService;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.SpaceReevaluationUseCase;
import edu.campusnexo.application.twin.ProcessCameraFrameService;
import edu.campusnexo.application.twin.ProcessEnvironmentReadingService;
import edu.campusnexo.application.twin.SpaceReevaluationService;
import edu.campusnexo.application.twin.TwinDecisionEngine;
import edu.campusnexo.application.twin.TwinPublisher;
import edu.campusnexo.application.twin.TwinWriter;
import edu.campusnexo.application.twin.RecentTraceBuffer;
import edu.campusnexo.application.twin.TwinControlService;
import edu.campusnexo.application.twin.TwinQueryService;
import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.port.RealtimeDashboardNotifierPort;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.RoomEquipmentCommandPort;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.IdleEquipmentShutdownPolicy;
import edu.campusnexo.domain.twin.KalmanParameters;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import edu.campusnexo.domain.twin.RoomConditionPolicy;
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
 * {@link TwinProperties}; the domain never sees Spring. Each input port (camera frames, Arduino readings,
 * re-evaluation after a manual control) is its own use case; they share the decision engine, the writer
 * that serializes and version-checks the writes of one room, and the publisher of the effects.
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
    TwinWriter twinWriter(SpaceRepositoryPort twins) {
        return new TwinWriter(twins);
    }

    @Bean
    TwinDecisionEngine twinDecisionEngine(ReservationRepository reservations, SpaceStatusPolicy statusPolicy,
                                          ThermalInertiaMpcDomainService thermal, EnergyAccountingPolicy energy,
                                          TwinProperties properties) {
        return new TwinDecisionEngine(reservations, statusPolicy, thermal, energy, new RoomConditionPolicy(),
                new IdleEquipmentShutdownPolicy(properties.status().equipmentIdleShutdown()));
    }

    @Bean
    TwinPublisher twinPublisher(SpaceRepositoryPort twins, HvacActuatorCommandPort actuator,
                                RoomEquipmentCommandPort equipment, RealtimeDashboardNotifierPort notifier,
                                RecentTraceBuffer traces) {
        return new TwinPublisher(twins, actuator, equipment, notifier, traces);
    }

    @Bean
    ProcessSpaceTelemetryUseCase processCameraFrame(Clock clock, CampusCatalogUseCase catalog, TelemetryUseCase telemetry,
                                                    SpaceRepositoryPort twins, OccupancyKalmanDomainService kalman,
                                                    TwinProperties properties, TwinWriter writer,
                                                    TwinDecisionEngine engine, TwinPublisher publisher) {
        return new ProcessCameraFrameService(clock, catalog, telemetry, twins, kalman,
                properties.status().deskAbandonThreshold(), writer, engine, publisher);
    }

    @Bean
    ProcessEnvironmentReadingUseCase processEnvironmentReading(Clock clock, CampusCatalogUseCase catalog,
                                                               TwinWriter writer, TwinDecisionEngine engine,
                                                               TwinPublisher publisher) {
        return new ProcessEnvironmentReadingService(clock, catalog, writer, engine, publisher);
    }

    @Bean
    SpaceReevaluationUseCase spaceReevaluation(Clock clock, CampusCatalogUseCase catalog, TwinWriter writer,
                                               TwinDecisionEngine engine, TwinPublisher publisher) {
        return new SpaceReevaluationService(clock, catalog, writer, engine, publisher);
    }

    @Bean
    TwinControlUseCase twinControl(Clock clock, CampusCatalogUseCase catalog, TwinWriter writer,
                                   SpaceReevaluationUseCase reevaluation) {
        return new TwinControlService(clock, catalog, writer, reevaluation);
    }

    @Bean
    TwinQueryUseCase twinQuery(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                               ReservationRepository reservations, EnergyAccountingPolicy energy, RecentTraceBuffer traces,
                               TwinProperties properties) {
        return new TwinQueryService(clock, catalog, twins, reservations, energy,
                properties.energy().emissionFactorKgCo2PerKwh(), traces);
    }

    @Bean
    SimulationUseCase simulation(Clock clock, ProcessSpaceTelemetryUseCase camera,
                                 ProcessEnvironmentReadingUseCase environment, TwinControlUseCase controls,
                                 ReservationUseCase reservations, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                                 TwinWriter writer, TwinQueryUseCase queries) {
        return new SimulationService(clock, camera, environment, controls, reservations, catalog, twins, writer, queries);
    }
}
