package edu.campusnexo.infrastructure.config;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.alert.AlertThresholds;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.application.port.in.TwinWatchdogUseCase;
import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.application.port.out.TwinChangeNotifierPort;
import edu.campusnexo.application.port.out.UnitOfWork;
import edu.campusnexo.application.twin.ProcessCameraFrameService;
import edu.campusnexo.application.twin.ProcessEnvironmentReadingService;
import edu.campusnexo.application.twin.RecentTraceBuffer;
import edu.campusnexo.application.twin.SeatRegions;
import edu.campusnexo.application.twin.SpaceReevaluationService;
import edu.campusnexo.application.twin.TwinControlService;
import edu.campusnexo.application.twin.TwinDecisionEngine;
import edu.campusnexo.application.twin.TwinPublisher;
import edu.campusnexo.application.twin.TwinQueryService;
import edu.campusnexo.application.twin.TwinWatchdogService;
import edu.campusnexo.application.twin.TwinWriter;
import edu.campusnexo.domain.climate.policy.ClimateFamilies;
import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.RoomEquipmentCommandPort;
import edu.campusnexo.domain.port.SeatRegionRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.telemetry.ReadingAdmission;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.IdleEquipmentShutdownPolicy;
import edu.campusnexo.domain.twin.KalmanParameters;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import edu.campusnexo.domain.twin.RoomConditionPolicy;
import edu.campusnexo.domain.twin.SpaceStatusPolicy;
import edu.campusnexo.domain.twin.ThermalInertiaMpcDomainService;
import edu.campusnexo.domain.twin.ThermalParameters;
import edu.campusnexo.domain.twin.hvac.HvacSettingChain;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the digital twin. Domain services are plain Java objects built here from
 * {@link TwinProperties}; the domain never sees Spring. Each input port (camera frames, Arduino readings,
 * re-evaluation after a manual control) is its own use case; they share the decision engine, the writer
 * that serializes, version-checks and commits the writes of one room, and the publisher of the effects.
 */
@Configuration
@EnableConfigurationProperties(TwinProperties.class)
class HexagonalBeanConfiguration {

    @Bean
    KalmanParameters kalmanParameters(TwinProperties properties) {
        TwinProperties.Kalman k = properties.kalman();
        return new KalmanParameters(k.processNoisePerMinute(), k.cameraVariance(), k.co2RelativeError(),
                k.wifiRelativeError(), k.peoplePerDevice(), k.outdoorCo2Ppm(), k.co2PpmPerPersonSqm());
    }

    @Bean
    OccupancyKalmanDomainService occupancyKalmanDomainService(KalmanParameters parameters) {
        return new OccupancyKalmanDomainService(parameters);
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
    SeatRegions seatRegions(SeatRegionRepository regions) {
        return new SeatRegions(regions);
    }

    @Bean
    TwinWriter twinWriter(SpaceRepositoryPort twins, UnitOfWork unitOfWork, OperationalMetrics metrics) {
        return new TwinWriter(twins, unitOfWork, metrics);
    }

    @Bean
    TwinDecisionEngine twinDecisionEngine(SpaceStatusPolicy statusPolicy, ThermalInertiaMpcDomainService thermal,
                                          ClimateFamilies families, EnergyAccountingPolicy energy,
                                          TwinProperties properties) {
        return new TwinDecisionEngine(statusPolicy, thermal, families, HvacSettingChain.standard(), energy,
                new RoomConditionPolicy(), new IdleEquipmentShutdownPolicy(properties.status().equipmentIdleShutdown()));
    }

    @Bean
    TwinPublisher twinPublisher(SpaceRepositoryPort twins, HvacActuatorCommandPort actuator,
                                RoomEquipmentCommandPort equipment, List<TwinChangeNotifierPort> notifiers,
                                RecentTraceBuffer traces) {
        return new TwinPublisher(twins, actuator, equipment, notifiers, traces);
    }

    @Bean
    ProcessSpaceTelemetryUseCase processCameraFrame(Clock clock, CampusCatalogUseCase catalog, TelemetryUseCase telemetry,
                                                    ReservationRepository reservations, SeatRegions seatRegions,
                                                    ReadingAdmission admission, OccupancyKalmanDomainService kalman,
                                                    TwinProperties properties, TwinWriter writer,
                                                    TwinDecisionEngine engine, TwinPublisher publisher,
                                                    OperationalMetrics metrics) {
        return new ProcessCameraFrameService(clock, catalog, telemetry, reservations, seatRegions, admission, kalman,
                properties.status().deskAbandonThreshold(), writer, engine, publisher, metrics);
    }

    @Bean
    ProcessEnvironmentReadingUseCase processEnvironmentReading(Clock clock, CampusCatalogUseCase catalog,
                                                               ReservationRepository reservations,
                                                               ReadingAdmission admission, TwinWriter writer,
                                                               TwinDecisionEngine engine, TwinPublisher publisher,
                                                               OperationalMetrics metrics) {
        return new ProcessEnvironmentReadingService(clock, catalog, reservations, admission, writer, engine, publisher,
                metrics);
    }

    @Bean
    SpaceReevaluationService spaceReevaluation(Clock clock, CampusCatalogUseCase catalog,
                                               ReservationRepository reservations, TwinWriter writer,
                                               TwinDecisionEngine engine, TwinPublisher publisher) {
        return new SpaceReevaluationService(clock, catalog, reservations, writer, engine, publisher);
    }

    @Bean
    TwinWatchdogUseCase twinWatchdog(Clock clock, SpaceRepositoryPort twins, SpaceReevaluationService reevaluation,
                                     AlertRecorder alerts, AlertThresholds thresholds,
                                     @Value("${app.twin.watchdog.interval}") Duration interval) {
        return new TwinWatchdogService(clock, twins, reevaluation, alerts, interval, thresholds.cooldown());
    }

    @Bean
    TwinControlUseCase twinControl(Clock clock, CampusCatalogUseCase catalog, SpaceReevaluationService reevaluation) {
        return new TwinControlService(clock, catalog, reevaluation);
    }

    @Bean
    TwinQueryUseCase twinQuery(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                               ReservationRepository reservations, SeatRegions seatRegions, EnergyAccountingPolicy energy,
                               RecentTraceBuffer traces, TwinProperties properties) {
        return new TwinQueryService(clock, catalog, twins, reservations, seatRegions, energy,
                properties.energy().emissionFactorKgCo2PerKwh(), traces);
    }
}
