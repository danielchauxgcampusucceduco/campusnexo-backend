package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.application.twin.TwinWriter.Change;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.telemetry.ReadingAdmission;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.Environment;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import edu.campusnexo.domain.twin.RoomEquipment;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A camera frame (YOLO + ByteTrack detections and the room sensors) updates the room: the Kalman filter
 * fuses camera, CO₂ and Wi-Fi, every desk region is evaluated by ray casting, the policies decide, and the
 * aggregated reading is recorded for the alert observers.
 *
 * <p>Order matters: the capture time is validated first, so a rejected frame changes nothing; a frame that
 * is not newer than the room's last frame (an MQTT redelivery) is ignored, which makes the use case
 * idempotent; the twin and the reading are committed in one transaction.
 */
public class ProcessCameraFrameService implements ProcessSpaceTelemetryUseCase {
    static final String SOURCE = "camara";

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TelemetryUseCase telemetry;
    private final ReservationRepository reservations;
    private final SeatRegions seatRegions;
    private final ReadingAdmission admission;
    private final OccupancyKalmanDomainService kalman;
    private final Duration abandonThreshold;
    private final TwinWriter writer;
    private final TwinDecisionEngine engine;
    private final TwinPublisher publisher;
    private final OperationalMetrics metrics;

    public ProcessCameraFrameService(Clock clock, CampusCatalogUseCase catalog, TelemetryUseCase telemetry,
                                     ReservationRepository reservations, SeatRegions seatRegions,
                                     ReadingAdmission admission, OccupancyKalmanDomainService kalman,
                                     Duration abandonThreshold, TwinWriter writer, TwinDecisionEngine engine,
                                     TwinPublisher publisher, OperationalMetrics metrics) {
        this.clock = clock;
        this.catalog = catalog;
        this.telemetry = telemetry;
        this.reservations = reservations;
        this.seatRegions = seatRegions;
        this.admission = admission;
        this.kalman = kalman;
        this.abandonThreshold = abandonThreshold;
        this.writer = writer;
        this.engine = engine;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Override
    public SpaceTwinState process(TelemetrySnapshot snapshot, String inboundAdapter) {
        CampusSpace space = catalog.getRequiredSpace(snapshot.spaceId());
        Instant now = snapshot.capturedAt();
        admission.check(now, clock.instant());
        ReservationContext context = ReservationContext.lookup(reservations, space.spaceId(), now);
        List<StudyZoneRoi> seats = seatRegions.of(space);

        TwinOutcome outcome = writer.update(space.spaceId(), () -> SpaceTwinState.initial(space, now), previous -> {
            if (!previous.sensing().acceptsCameraFrame(now)) {
                return Change.unchanged(TwinOutcome.unchanged(previous));
            }
            TwinOutcome decided = decide(space, previous, snapshot, seats, context, inboundAdapter);
            return new Change<>(decided.state(), decided, () -> record(snapshot, decided.state()));
        });
        if (!outcome.changed()) {
            metrics.readingDiscarded(SOURCE, outcome.state().sensing().lastCameraFrameAt().equals(now)
                    ? "duplicada" : "fuera_de_orden");
            return outcome.state();
        }
        outcome.trace().step(HexagonalLayer.USE_CASE, TelemetryUseCase.class,
                "Lectura guardada en la misma transacción; observadores de alertas notificados al confirmar");
        return publisher.publish(outcome);
    }

    /** The reading of this frame, with the camera's own count kept apart from the fused estimate. */
    private void record(TelemetrySnapshot snapshot, SpaceTwinState updated) {
        telemetry.ingest(new RecordTelemetryCommand(snapshot.spaceId(), snapshot.capturedAt(),
                updated.occupancy().people(), snapshot.belongingsCount(), snapshot.wifiDevices(), snapshot.co2Ppm(),
                snapshot.indoorTemperatureC(), snapshot.humidityPct(), snapshot.illuminanceLux(),
                snapshot.activePowerKw(), snapshot.cameraPeopleCount()));
    }

    private TwinOutcome decide(CampusSpace space, SpaceTwinState previous, TelemetrySnapshot snapshot,
                               List<StudyZoneRoi> seats, ReservationContext context, String inboundAdapter) {
        Instant now = snapshot.capturedAt();
        TraceRecorder trace = new TraceRecorder(inboundAdapter, space.spaceId(), clock.instant())
                .step(HexagonalLayer.INBOUND_ADAPTER, inboundAdapter,
                        snapshot.detections().size() + " detecciones, cámara " + (snapshot.cameraOnline() ? "en línea" : "caída"))
                .step(HexagonalLayer.INPUT_PORT, ProcessSpaceTelemetryUseCase.class, "process(TelemetrySnapshot)")
                .step(HexagonalLayer.USE_CASE, ProcessCameraFrameService.class, "Orquesta Kalman → ROI → estado → MPC");

        KalmanStep step = kalman.update(previous.occupancy(), snapshot, space);
        trace.step(HexagonalLayer.DOMAIN_SERVICE, OccupancyKalmanDomainService.class, String.format(Locale.ROOT,
                "x⁻=%.1f P⁻=%.2f → x=%.1f P=%.2f (%d mediciones)", previous.occupancy().estimate(),
                step.predictedVariance(), step.posterior().estimate(), step.posterior().variance(), step.measurements().size()));

        List<DeskObservation> desks = previous.desks();
        if (snapshot.cameraOnline()) {
            desks = evaluateDesks(seats, snapshot, previous.desks(), now);
            if (!desks.isEmpty()) {
                long held = desks.stream().filter(d -> d.unattendedSince() != null).count();
                long seated = desks.stream().filter(d -> d.peopleInside() > 0).count();
                trace.step(HexagonalLayer.DOMAIN_SERVICE, StudyZoneRoi.class,
                        desks.size() + " puestos evaluados por ray casting: " + seated + " ocupados, " + held
                                + " con pertenencias sin estudiante");
            }
        }
        RoomEquipment equipment = snapshot.lightsOn() == null ? previous.equipment()
                : TwinDecisionEngine.equipment(space, snapshot.lightsOn(), snapshot.illuminanceLux(),
                snapshot.devicesOn() == null ? previous.equipment().devicesOn() : snapshot.devicesOn());
        double outdoorC = snapshot.outdoorTemperatureC() == null ? space.averageOutsideTempC() : snapshot.outdoorTemperatureC();
        Environment environment = new Environment(snapshot.indoorTemperatureC(), outdoorC, snapshot.co2Ppm(),
                snapshot.humidityPct());
        return engine.decide(space, previous, new TwinDecisionEngine.Inputs(step.posterior(), step, desks, environment,
                previous.sensing().cameraFrame(snapshot.cameraOnline(), now), equipment, now), context, trace);
    }

    private List<DeskObservation> evaluateDesks(List<StudyZoneRoi> seats, TelemetrySnapshot snapshot,
                                                List<DeskObservation> previousDesks, Instant now) {
        Map<String, DeskObservation> previousByZone = previousDesks.stream()
                .collect(Collectors.toMap(DeskObservation::zoneId, Function.identity(), (a, b) -> b));
        return seats.stream()
                .map(roi -> roi.evaluateDeskGhosting(snapshot.detections(), previousByZone.get(roi.zoneId()), now,
                        abandonThreshold))
                .toList();
    }
}
