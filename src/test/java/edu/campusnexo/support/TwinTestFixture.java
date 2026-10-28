package edu.campusnexo.support;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.out.UnitOfWork;
import edu.campusnexo.application.reservation.ReservationService;
import edu.campusnexo.application.telemetry.TelemetryEventPublisher;
import edu.campusnexo.application.telemetry.TelemetryService;
import edu.campusnexo.application.twin.ProcessCameraFrameService;
import edu.campusnexo.application.twin.ProcessEnvironmentReadingService;
import edu.campusnexo.application.twin.RecentTraceBuffer;
import edu.campusnexo.application.twin.SeatRegions;
import edu.campusnexo.application.twin.SpaceReevaluationService;
import edu.campusnexo.application.twin.TwinControlService;
import edu.campusnexo.application.twin.TwinDecisionEngine;
import edu.campusnexo.application.twin.TwinPublisher;
import edu.campusnexo.application.twin.TwinQueryService;
import edu.campusnexo.application.twin.TwinWriter;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.climate.policy.ClimateFamilies;
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
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Wires the digital-twin use cases with in-memory adapters, as the composition root does in production. */
public final class TwinTestFixture {
    public final MutableClock clock = new MutableClock(TestData.NOW);
    public final RecordingMetrics metrics = new RecordingMetrics();
    public final InMemoryTelemetryRepository telemetryRows = new InMemoryTelemetryRepository();
    public final InMemoryReservationRepository reservationRows = new InMemoryReservationRepository();
    public final InMemorySpaceRepositoryPort twins = new InMemorySpaceRepositoryPort()
            .withRois("BIB-PISO-2", InMemorySpaceRepositoryPort.libraryDesks())
            .withRois("BIBLIOTECA-2B", InMemorySpaceRepositoryPort.libraryDesks("BIBLIOTECA-2B", "BIB2B-M"));
    public final SeatRegions seatRegions = new SeatRegions(twins);
    public final RecordingOutboundAdapters.Actuator actuator = new RecordingOutboundAdapters.Actuator();
    public final RecordingOutboundAdapters.EquipmentActuator equipment = new RecordingOutboundAdapters.EquipmentActuator();
    public final RecordingOutboundAdapters.Notifier notifier = new RecordingOutboundAdapters.Notifier();
    public final RecentTraceBuffer traces = new RecentTraceBuffer(50);
    public final CampusCatalogUseCase catalog = new SpaceCatalogService(new InMemorySpaceCatalogRepository(spaces()));
    public final InMemoryPersonaRepository personas = new InMemoryPersonaRepository();
    public final ReservationService reservations = new ReservationService(clock, reservationRows, catalog, personas, twins);
    public final EnergyAccountingPolicy energy = new EnergyAccountingPolicy(0.12, Duration.ofMinutes(15),
            LocalTime.of(7, 0), LocalTime.of(21, 0), ZoneId.of("America/Bogota"));
    public final ReadingAdmission admission = new ReadingAdmission(Duration.ofMinutes(5), Duration.ofDays(30));
    public final TwinWriter writer = new TwinWriter(twins, UnitOfWork.direct(), metrics);
    public final TwinDecisionEngine engine = new TwinDecisionEngine(new SpaceStatusPolicy(Duration.ofMinutes(15), 0.3),
            new ThermalInertiaMpcDomainService(ThermalParameters.defaults()), ClimateFamilies.standard(),
            HvacSettingChain.standard(), energy, new RoomConditionPolicy(),
            new IdleEquipmentShutdownPolicy(Duration.ofMinutes(5)));
    public final TwinPublisher publisher = new TwinPublisher(twins, actuator, equipment, List.of(notifier), traces);
    public final TelemetryService telemetry = new TelemetryService(clock, telemetryRows, catalog,
            new TelemetryEventPublisher(List.of(), (observer, event, failure) -> { }), admission, UnitOfWork.direct());
    public final ProcessCameraFrameService pipeline = new ProcessCameraFrameService(clock, catalog, telemetry,
            reservationRows, seatRegions, admission, new OccupancyKalmanDomainService(KalmanParameters.defaults()),
            Duration.ofMinutes(15), writer, engine, publisher, metrics);
    public final ProcessEnvironmentReadingService environment = new ProcessEnvironmentReadingService(clock, catalog,
            reservationRows, admission, writer, engine, publisher, metrics);
    public final SpaceReevaluationService reevaluation = new SpaceReevaluationService(clock, catalog, reservationRows,
            writer, engine, publisher);
    public final TwinControlService controls = new TwinControlService(clock, catalog, reevaluation);
    public final TwinQueryService queries = new TwinQueryService(clock, catalog, twins, reservationRows, seatRegions,
            energy, 0.126, traces);

    private static CampusSpace[] spaces() {
        List<CampusSpace> all = new ArrayList<>(List.of(TestData.andeanClassroom(), TestData.tropicalClassroom(),
                TestData.mildClassroom(), TestData.library(), TestData.lab()));
        all.addAll(TestData.campusRooms());
        return all.toArray(CampusSpace[]::new);
    }
}
