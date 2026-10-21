package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.twin.TwinWriter.Change;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import edu.campusnexo.domain.twin.RoomEquipment;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.DeskObservation;
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
 * fuses camera, CO₂ and Wi-Fi, every desk region is evaluated by ray casting, the policies decide, and
 * the aggregated reading is recorded for the alert observers.
 */
public class ProcessCameraFrameService implements ProcessSpaceTelemetryUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TelemetryUseCase telemetry;
    private final SpaceRepositoryPort twins;
    private final OccupancyKalmanDomainService kalman;
    private final Duration abandonThreshold;
    private final TwinWriter writer;
    private final TwinDecisionEngine engine;
    private final TwinPublisher publisher;

    public ProcessCameraFrameService(Clock clock, CampusCatalogUseCase catalog, TelemetryUseCase telemetry,
                                     SpaceRepositoryPort twins, OccupancyKalmanDomainService kalman,
                                     Duration abandonThreshold, TwinWriter writer, TwinDecisionEngine engine,
                                     TwinPublisher publisher) {
        this.clock = clock;
        this.catalog = catalog;
        this.telemetry = telemetry;
        this.twins = twins;
        this.kalman = kalman;
        this.abandonThreshold = abandonThreshold;
        this.writer = writer;
        this.engine = engine;
        this.publisher = publisher;
    }

    @Override
    public SpaceTwinState process(TelemetrySnapshot snapshot, String inboundAdapter) {
        CampusSpace space = catalog.getRequiredSpace(snapshot.spaceId());
        Instant now = snapshot.capturedAt();
        TwinOutcome outcome = writer.update(space.spaceId(), () -> SpaceTwinState.initial(space, now), previous -> {
            TwinOutcome decided = decide(space, previous, snapshot, inboundAdapter);
            return new Change<>(decided.state(), decided);
        });

        SpaceTwinState updated = outcome.state();
        telemetry.ingest(new RecordTelemetryCommand(space.spaceId(), now, updated.occupancy().people(),
                snapshot.belongingsCount(), snapshot.wifiDevices(), snapshot.co2Ppm(), snapshot.indoorTemperatureC(),
                snapshot.humidityPct(), snapshot.illuminanceLux(), snapshot.activePowerKw()));
        outcome.trace().step(HexagonalLayer.USE_CASE, "TelemetryUseCase.ingest",
                "Lectura agregada guardada; observadores de alertas notificados");
        return publisher.publish(outcome);
    }

    private TwinOutcome decide(CampusSpace space, SpaceTwinState previous, TelemetrySnapshot snapshot,
                               String inboundAdapter) {
        Instant now = snapshot.capturedAt();
        TraceRecorder trace = new TraceRecorder(inboundAdapter, space.spaceId(), clock.instant())
                .step(HexagonalLayer.INBOUND_ADAPTER, inboundAdapter,
                        snapshot.detections().size() + " detecciones, cámara " + (snapshot.cameraOnline() ? "en línea" : "caída"))
                .step(HexagonalLayer.INPUT_PORT, "ProcessSpaceTelemetryUseCase", "process(TelemetrySnapshot)")
                .step(HexagonalLayer.USE_CASE, "ProcessCameraFrameService", "Orquesta Kalman → ROI → estado → MPC");

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
                : TwinDecisionEngine.equipment(space, snapshot.lightsOn(), snapshot.illuminanceLux(),
                snapshot.devicesOn() == null ? previous.equipment().devicesOn() : snapshot.devicesOn());
        double outdoorC = snapshot.outdoorTemperatureC() == null ? space.averageOutsideTempC() : snapshot.outdoorTemperatureC();
        return engine.decide(space, previous, new TwinDecisionEngine.Inputs(step.posterior(), step, desks,
                snapshot.indoorTemperatureC(), outdoorC, snapshot.co2Ppm(), snapshot.humidityPct(),
                snapshot.cameraOnline(), equipment, now), trace);
    }

    private List<DeskObservation> evaluateDesks(CampusSpace space, TelemetrySnapshot snapshot,
                                                List<DeskObservation> previousDesks, Instant now) {
        Map<String, DeskObservation> previousByZone = previousDesks.stream()
                .collect(Collectors.toMap(DeskObservation::zoneId, Function.identity(), (a, b) -> b));
        return SeatRegions.of(space, twins).stream()
                .map(roi -> roi.evaluateDeskGhosting(snapshot.detections(), previousByZone.get(roi.zoneId()), now,
                        abandonThreshold))
                .toList();
    }
}
