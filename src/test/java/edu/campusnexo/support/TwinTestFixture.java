package edu.campusnexo.support;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.reservation.ReservationService;
import edu.campusnexo.application.simulation.SimulationService;
import edu.campusnexo.application.telemetry.TelemetryEventPublisher;
import edu.campusnexo.application.telemetry.TelemetryService;
import edu.campusnexo.application.twin.ProcessSpaceTelemetryService;
import edu.campusnexo.application.twin.RecentTraceBuffer;
import edu.campusnexo.application.twin.TwinControlService;
import edu.campusnexo.application.twin.TwinQueryService;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.KalmanParameters;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import edu.campusnexo.domain.twin.RoomConditionPolicy;
import edu.campusnexo.domain.twin.SpaceStatusPolicy;
import edu.campusnexo.domain.twin.ThermalInertiaMpcDomainService;
import edu.campusnexo.domain.twin.ThermalParameters;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Wires the digital-twin use cases with in-memory adapters, as the composition root does in production. */
public final class TwinTestFixture {
    public final MutableClock clock = new MutableClock(TestData.NOW);
    public final InMemoryTelemetryRepository telemetryRows = new InMemoryTelemetryRepository();
    public final InMemoryReservationRepository reservationRows = new InMemoryReservationRepository();
    public final InMemorySpaceRepositoryPort twins = new InMemorySpaceRepositoryPort()
            .withRois("BIB-PISO-2", InMemorySpaceRepositoryPort.libraryDesks())
            .withRois("BIBLIOTECA-2B", InMemorySpaceRepositoryPort.libraryDesks("BIBLIOTECA-2B", "BIB2B-M"));
    public final RecordingOutboundAdapters.Actuator actuator = new RecordingOutboundAdapters.Actuator();
    public final RecordingOutboundAdapters.EquipmentActuator equipment = new RecordingOutboundAdapters.EquipmentActuator();
    public final RecordingOutboundAdapters.Notifier notifier = new RecordingOutboundAdapters.Notifier();
    public final RecentTraceBuffer traces = new RecentTraceBuffer(20);
    public final CampusCatalogUseCase catalog = new SpaceCatalogService(new InMemorySpaceCatalogRepository(spaces()));
    public final ReservationService reservations = new ReservationService(clock, reservationRows, catalog);
    public final EnergyAccountingPolicy energy = new EnergyAccountingPolicy(0.12, Duration.ofMinutes(15),
            LocalTime.of(7, 0), LocalTime.of(21, 0), ZoneId.of("America/Bogota"));
    public final ProcessSpaceTelemetryService pipeline = new ProcessSpaceTelemetryService(clock, catalog,
            new TelemetryService(clock, telemetryRows, catalog,
                    new TelemetryEventPublisher(List.of(), (observer, event, failure) -> { }),
                    Duration.ofMinutes(5), Duration.ofDays(30)),
            reservationRows, twins, actuator, notifier,
            new OccupancyKalmanDomainService(KalmanParameters.defaults()),
            new ThermalInertiaMpcDomainService(ThermalParameters.defaults()),
            new SpaceStatusPolicy(Duration.ofMinutes(15), 0.3), energy, Duration.ofMinutes(15), traces, equipment,
            new RoomConditionPolicy(), Duration.ofMinutes(5));
    public final TwinControlService controls = new TwinControlService(clock, catalog, twins, pipeline);
    public final TwinQueryService queries = new TwinQueryService(clock, catalog, twins, reservationRows, energy, 0.126, traces);
    public final SimulationService simulation = new SimulationService(clock, pipeline, pipeline, controls, reservations,
            catalog, twins, queries);

    private static edu.campusnexo.domain.catalog.CampusSpace[] spaces() {
        List<edu.campusnexo.domain.catalog.CampusSpace> all = new ArrayList<>(List.of(TestData.andeanClassroom(),
                TestData.tropicalClassroom(), TestData.mildClassroom(), TestData.library(), TestData.lab()));
        all.addAll(TestData.scenarioRooms());
        return all.toArray(edu.campusnexo.domain.catalog.CampusSpace[]::new);
    }
}
