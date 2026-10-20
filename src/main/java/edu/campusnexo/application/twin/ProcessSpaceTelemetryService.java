package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import edu.campusnexo.application.port.in.SpaceReevaluationUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.port.RealtimeDashboardNotifierPort;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.RoomEquipmentCommandPort;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.ComfortBand;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.HvacCommand;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.RoomConditionPolicy;
import edu.campusnexo.domain.twin.RoomEquipment;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceStatusPolicy;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.twin.ThermalContext;
import edu.campusnexo.domain.twin.ThermalDecision;
import edu.campusnexo.domain.twin.ThermalInertiaMpcDomainService;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Core use case of the digital twin. For each frame it:
 * <ol>
 *   <li>fuses camera, CO2 and Wi-Fi with the Kalman filter;</li>
 *   <li>evaluates every desk region (ray casting + anti-hoarding timer);</li>
 *   <li>classifies the space against capacity and reservations;</li>
 *   <li>chooses the HVAC strategy with the thermal-inertia controller;</li>
 *   <li>classifies the room's overall condition (people, lights, computers) and, when it has been empty
 *       with equipment on for longer than the idle threshold, orders the Arduino to switch it off;</li>
 *   <li>records the aggregated reading (which feeds the alert observers), stores the twin, commands the
 *       HVAC only when the strategy changes and notifies the dashboards with the architecture trace.</li>
 * </ol>
 */
public class ProcessSpaceTelemetryService
        implements ProcessSpaceTelemetryUseCase, ProcessEnvironmentReadingUseCase, SpaceReevaluationUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TelemetryUseCase telemetry;
    private final ReservationRepository reservations;
    private final SpaceRepositoryPort twins;
    private final HvacActuatorCommandPort actuator;
    private final RealtimeDashboardNotifierPort notifier;
    private final OccupancyKalmanDomainService kalman;
    private final ThermalInertiaMpcDomainService thermal;
    private final SpaceStatusPolicy statusPolicy;
    private final EnergyAccountingPolicy energy;
    private final Duration abandonThreshold;
    private final RecentTraceBuffer traceBuffer;
    private final RoomEquipmentCommandPort equipmentActuator;
    private final RoomConditionPolicy conditionPolicy;
    private final Duration idleShutdown;

    public ProcessSpaceTelemetryService(Clock clock, CampusCatalogUseCase catalog, TelemetryUseCase telemetry,
                                        ReservationRepository reservations, SpaceRepositoryPort twins,
                                        HvacActuatorCommandPort actuator, RealtimeDashboardNotifierPort notifier,
                                        OccupancyKalmanDomainService kalman, ThermalInertiaMpcDomainService thermal,
                                        SpaceStatusPolicy statusPolicy, EnergyAccountingPolicy energy,
                                        Duration abandonThreshold, RecentTraceBuffer traceBuffer,
                                        RoomEquipmentCommandPort equipmentActuator, RoomConditionPolicy conditionPolicy,
                                        Duration idleShutdown) {
        this.clock = clock;
        this.catalog = catalog;
        this.telemetry = telemetry;
        this.reservations = reservations;
        this.twins = twins;
        this.actuator = actuator;
        this.notifier = notifier;
        this.kalman = kalman;
        this.thermal = thermal;
        this.statusPolicy = statusPolicy;
        this.energy = energy;
        this.abandonThreshold = abandonThreshold;
        this.traceBuffer = traceBuffer;
        this.equipmentActuator = equipmentActuator;
        this.conditionPolicy = conditionPolicy;
        this.idleShutdown = idleShutdown;
    }

    @Override
    public SpaceTwinState process(TelemetrySnapshot snapshot, String inboundAdapter) {
        TraceRecorder trace = new TraceRecorder(inboundAdapter, snapshot.spaceId(), clock.instant())
                .step(HexagonalLayer.INBOUND_ADAPTER, inboundAdapter,
                        snapshot.detections().size() + " detecciones, cámara " + (snapshot.cameraOnline() ? "en línea" : "caída"))
                .step(HexagonalLayer.INPUT_PORT, "ProcessSpaceTelemetryUseCase", "process(TelemetrySnapshot)")
                .step(HexagonalLayer.USE_CASE, "ProcessSpaceTelemetryService", "Orquesta Kalman → ROI → estado → MPC");

        CampusSpace space = catalog.getRequiredSpace(snapshot.spaceId());
        SpaceTwinState previous = twins.findTwin(space.spaceId())
                .orElseGet(() -> SpaceTwinState.initial(space, snapshot.capturedAt()));
        Instant now = snapshot.capturedAt();

        KalmanStep step = kalman.update(previous.occupancy(), snapshot, space);
        trace.step(HexagonalLayer.DOMAIN_SERVICE, "OccupancyKalmanDomainService", String.format(Locale.ROOT,
                "x⁻=%.1f P⁻=%.2f → x=%.1f P=%.2f (%d mediciones)", previous.occupancy().estimate(),
                step.predictedVariance(), step.posterior().estimate(), step.posterior().variance(), step.measurements().size()));

        List<DeskObservation> desks = previous.desks();
        if (snapshot.cameraOnline()) {
            desks = evaluateDesks(space, snapshot, previous.desks(), now);
            if (!desks.isEmpty()) {
                long held = desks.stream().filter(d -> d.unattendedSince() != null).count();
                long seated = desks.stream().filter(d -> d.peopleInside() > 0).count();
                trace.step(HexagonalLayer.DOMAIN_SERVICE, "StudyZoneRoi.evaluateDeskGhosting",
                        desks.size() + " puestos evaluados por ray casting: " + seated + " ocupados, " + held
                                + " con pertenencias sin estudiante");
            }
        }
        RoomEquipment equipment = snapshot.lightsOn() == null ? previous.equipment()
                : equipment(space, snapshot.lightsOn(), snapshot.illuminanceLux(),
                snapshot.devicesOn() == null ? previous.equipment().devicesOn() : snapshot.devicesOn());

        Sensors sensors = new Sensors(snapshot.indoorTemperatureC(),
                snapshot.outdoorTemperatureC() == null ? space.averageOutsideTempC() : snapshot.outdoorTemperatureC(),
                snapshot.co2Ppm(), snapshot.humidityPct(), snapshot.cameraOnline());
        Outcome outcome = decide(space, previous, step.posterior(), step, desks, sensors, equipment, now, trace);
        SpaceTwinState updated = outcome.state();

        telemetry.ingest(new RecordTelemetryCommand(space.spaceId(), now, updated.occupancy().people(),
                snapshot.belongingsCount(), snapshot.wifiDevices(), snapshot.co2Ppm(), snapshot.indoorTemperatureC(),
                snapshot.humidityPct(), snapshot.illuminanceLux(), snapshot.activePowerKw()));
        trace.step(HexagonalLayer.USE_CASE, "TelemetryUseCase.ingest",
                "Lectura agregada guardada; observadores de alertas notificados");

        return persistAndPublish(space, previous, outcome, trace);
    }

    @Override
    public SpaceTwinState processEnvironment(EnvironmentReading reading, String inboundAdapter) {
        CampusSpace space = catalog.getRequiredSpace(reading.spaceId());
        TraceRecorder trace = new TraceRecorder(inboundAdapter, space.spaceId(), clock.instant())
                .step(HexagonalLayer.INBOUND_ADAPTER, inboundAdapter, String.format(Locale.ROOT,
                        "%.1f °C, %.0f %% HR, luces %s, %d equipos encendidos", reading.temperatureC(),
                        reading.humidityPct(), reading.lightsOn() ? "encendidas" : "apagadas", reading.devicesOn()))
                .step(HexagonalLayer.INPUT_PORT, "ProcessEnvironmentReadingUseCase", "processEnvironment(EnvironmentReading)")
                .step(HexagonalLayer.USE_CASE, "ProcessSpaceTelemetryService", "Actualiza el ambiente y recalcula el salón");
        Instant now = reading.capturedAt();
        SpaceTwinState previous = twins.findTwin(space.spaceId()).orElseGet(() -> SpaceTwinState.initial(space, now));
        Instant evaluationTime = now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now;
        Sensors sensors = new Sensors(reading.temperatureC(), previous.outdoorTemperatureC(),
                reading.co2Ppm() == null ? previous.co2Ppm() : reading.co2Ppm(), reading.humidityPct(),
                previous.cameraOnline());
        RoomEquipment equipment = equipment(space, reading.lightsOn(), reading.illuminanceLux(), reading.devicesOn());
        Outcome outcome = decide(space, previous, previous.occupancy(), previous.lastKalmanStep(), previous.desks(),
                sensors, equipment, evaluationTime, trace);
        return persistAndPublish(space, previous, outcome, trace);
    }

    @Override
    public SpaceTwinState reevaluate(String spaceId, String trigger) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        TraceRecorder trace = new TraceRecorder(trigger, spaceId, now)
                .step(HexagonalLayer.INBOUND_ADAPTER, trigger, "Cambio de control manual")
                .step(HexagonalLayer.INPUT_PORT, "SpaceReevaluationUseCase", "reevaluate(" + spaceId + ")")
                .step(HexagonalLayer.USE_CASE, "ProcessSpaceTelemetryService", "Recalcula estado y estrategia HVAC");
        SpaceTwinState previous = twins.findTwin(spaceId).orElseGet(() -> SpaceTwinState.initial(space, now));
        Instant evaluationTime = now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now;
        Sensors sensors = new Sensors(previous.indoorTemperatureC(), previous.outdoorTemperatureC(), previous.co2Ppm(),
                previous.humidityPct(), previous.cameraOnline());
        Outcome outcome = decide(space, previous, previous.occupancy(), previous.lastKalmanStep(),
                previous.desks(), sensors, previous.equipment(), evaluationTime, trace);
        return persistAndPublish(space, previous, outcome, trace);
    }

    private Outcome decide(CampusSpace space, SpaceTwinState previous, OccupancyBelief belief, KalmanStep step,
                           List<DeskObservation> desks, Sensors sensors, RoomEquipment equipment, Instant now,
                           TraceRecorder trace) {
        int people = belief.people();
        Reservation active = reservations.findActiveAt(space.spaceId(), now).orElse(null);
        SpaceStatus status = statusPolicy.classify(previous.maintenance(), previous.popUpActive(now), people,
                space.capacity(), active, now);
        trace.step(HexagonalLayer.DOMAIN_SERVICE, "SpaceStatusPolicy",
                people + "/" + space.capacity() + " personas" + (active == null ? "" : ", reserva " + active.reference())
                        + " → " + status);

        ThermalDecision decision = thermal.decide(new ThermalContext(sensors.indoorC(), sensors.outdoorC(), people,
                space.areaSqm(), sensors.co2Ppm(), ComfortBand.forProfile(space.climateProfile()),
                minutesUntilOccupancyEnds(active, people, now), minutesUntilNextOccupancy(space.spaceId(), active, status, now)));
        HvacControlState hvacState = previous.maintenance() ? HvacControlState.APAGADO
                : previous.overrideActive(now) ? HvacControlState.OVERRIDE_MANUAL_DOCENTE
                : decision.state();
        trace.step(HexagonalLayer.DOMAIN_SERVICE, "ThermalInertiaMpcDomainService", hvacState + ": " + decision.explanation());

        boolean firstFrame = previous.lastKalmanStep() == null;
        double saved = firstFrame ? previous.energySavedKwh()
                : previous.energySavedKwh() + energy.savedKwh(previous.hvacState(), previous.updatedAt(), now, space.areaSqm());
        Instant hvacSince = hvacState == previous.hvacState() && !firstFrame ? previous.hvacStateSince() : now;

        Instant emptySince = people > 0 ? null : previous.emptySince() != null ? previous.emptySince() : now;
        RoomCondition condition = conditionPolicy.classify(previous.maintenance(), people, space.capacity(), equipment);
        EquipmentCommand command = null;
        Instant shutdownAt = previous.equipmentShutdownAt();
        if (condition.wastesEnergy() && Duration.between(emptySince, now).compareTo(idleShutdown) >= 0
                && (shutdownAt == null || shutdownAt.isBefore(emptySince)
                    || Duration.between(shutdownAt, now).compareTo(idleShutdown) >= 0)) {
            long idleMinutes = Duration.between(emptySince, now).toMinutes();
            command = new EquipmentCommand(UUID.randomUUID(), space.spaceId(), equipment.lightsOn(),
                    equipment.devicesOn() > 0, now, "Salón vacío hace " + idleMinutes + " min con "
                    + describe(equipment));
            // The twin assumes the order is executed; the next Arduino reading confirms or corrects it.
            equipment = equipment.shutDown();
            shutdownAt = now;
            condition = conditionPolicy.classify(previous.maintenance(), people, space.capacity(), equipment);
        }
        trace.step(HexagonalLayer.DOMAIN_SERVICE, "RoomConditionPolicy", people + "/" + space.capacity()
                + " personas, " + describe(equipment) + " → " + condition);

        SpaceTwinState state = new SpaceTwinState(space.spaceId(), status, hvacState, hvacSince, belief, step, decision,
                sensors.indoorC(), sensors.outdoorC(), sensors.co2Ppm(), sensors.humidityPct(), sensors.cameraOnline(),
                desks, round(saved), previous.overrideUntil(), previous.popUpUntil(), previous.maintenance(),
                now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now, equipment, condition, emptySince,
                shutdownAt);
        return new Outcome(state, command);
    }

    private static RoomEquipment equipment(CampusSpace space, boolean lightsOn, double illuminanceLux, int devicesOn) {
        return new RoomEquipment(lightsOn, illuminanceLux, Math.min(devicesOn, space.computers()), space.computers());
    }

    private static String describe(RoomEquipment equipment) {
        return "luces " + (equipment.lightsOn() ? "encendidas" : "apagadas") + ", " + equipment.devicesOn() + "/"
                + equipment.devicesTotal() + " equipos encendidos";
    }

    private SpaceTwinState persistAndPublish(CampusSpace space, SpaceTwinState previous, Outcome outcome,
                                             TraceRecorder trace) {
        SpaceTwinState saved = twins.saveTwin(outcome.state());
        trace.outbound("SpaceRepositoryPort.saveTwin", twins, saved.status() + " / " + saved.hvacState());

        boolean strategyChanged = saved.hvacState() != previous.hvacState() || previous.lastKalmanStep() == null;
        if (strategyChanged) {
            actuator.send(HvacCommand.of(space.spaceId(), saved.lastThermalDecision(), saved.hvacState(), saved.updatedAt()));
            trace.outbound("HvacActuatorCommandPort.send", actuator, previous.hvacState() + " → " + saved.hvacState());
        }
        EquipmentCommand command = outcome.equipmentCommand();
        if (command != null) {
            equipmentActuator.send(command);
            trace.outbound("RoomEquipmentCommandPort.send", equipmentActuator,
                    (command.lightsOff() ? "apagar luces" : "") + (command.lightsOff() && command.suspendDevices() ? " y " : "")
                            + (command.suspendDevices() ? "suspender equipos" : ""));
        }
        notifier.publishTwinUpdate(saved);
        trace.outbound("RealtimeDashboardNotifierPort.publishTwinUpdate", notifier, "Gemelo actualizado");

        ArchitectureTrace finished = trace.finish();
        traceBuffer.add(finished);
        notifier.publishTrace(finished);
        return saved;
    }

    private List<DeskObservation> evaluateDesks(CampusSpace space, TelemetrySnapshot snapshot,
                                                List<DeskObservation> previousDesks, Instant now) {
        Map<String, DeskObservation> previousByZone = previousDesks.stream()
                .collect(Collectors.toMap(DeskObservation::zoneId, Function.identity(), (a, b) -> b));
        return SeatRegions.of(space, twins).stream()
                .map((StudyZoneRoi roi) -> roi.evaluateDeskGhosting(snapshot.detections(), previousByZone.get(roi.zoneId()),
                        now, abandonThreshold))
                .toList();
    }

    private static Integer minutesUntilOccupancyEnds(Reservation active, int people, Instant now) {
        if (active == null || people == 0) {
            return null;
        }
        return (int) Math.max(0, Duration.between(now, active.endsAt()).toMinutes());
    }

    private Integer minutesUntilNextOccupancy(String spaceId, Reservation active, SpaceStatus status, Instant now) {
        if (active != null && status == SpaceStatus.RESERVADO_EN_ESPERA) {
            return 0;
        }
        Instant after = active != null ? active.endsAt() : now;
        return reservations.findNextStartingAfter(spaceId, after)
                .map(next -> (int) Math.max(0, Duration.between(now, next.startsAt()).toMinutes()))
                .orElse(null);
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private record Sensors(double indoorC, double outdoorC, double co2Ppm, double humidityPct, boolean cameraOnline) {
    }

    private record Outcome(SpaceTwinState state, EquipmentCommand equipmentCommand) {
    }
}
