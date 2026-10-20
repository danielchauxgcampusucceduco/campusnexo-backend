package edu.campusnexo.application.simulation;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.application.twin.SeatRegions;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.domain.vision.ZoneDeskState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstration scenarios for the thesis defense. Each scenario only arranges preconditions
 * (reservations, manual controls, how long a room has been empty, previous desk timers) and then sends
 * synthetic camera frames and Arduino readings through the same use cases that real MQTT messages use,
 * so the demo exercises the production code path. Reservations created here carry the
 * {@value #RESERVATION_PREFIX} prefix.
 */
public class SimulationService implements SimulationUseCase {
    static final String RESERVATION_PREFIX = "SIM-";
    static final String INBOUND = "REST · SimulationController";
    static final String ARDUINO = "REST · SimulationController (lectura Arduino)";
    private static final double PERSON_WIDTH = 0.045;
    private static final double PERSON_HEIGHT = 0.07;
    private static final double OUTDOOR_C = 13.5;

    private final Clock clock;
    private final ProcessSpaceTelemetryUseCase pipeline;
    private final ProcessEnvironmentReadingUseCase environment;
    private final TwinControlUseCase controls;
    private final ReservationUseCase reservations;
    private final CampusCatalogUseCase catalog;
    private final SpaceRepositoryPort twins;
    private final TwinQueryUseCase queries;

    public SimulationService(Clock clock, ProcessSpaceTelemetryUseCase pipeline,
                             ProcessEnvironmentReadingUseCase environment, TwinControlUseCase controls,
                             ReservationUseCase reservations, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                             TwinQueryUseCase queries) {
        this.clock = clock;
        this.pipeline = pipeline;
        this.environment = environment;
        this.controls = controls;
        this.reservations = reservations;
        this.catalog = catalog;
        this.twins = twins;
        this.queries = queries;
    }

    @Override
    public SimulationResult run(SimulationScenario scenario) {
        Instant now = clock.instant();
        String room = scenario.spaceId();
        switch (scenario) {
            case CLASE_EN_CURSO -> {
                reserve(room, now.minus(minutes(10)), now.plus(minutes(80)), 28, scenario);
                send(seatedFrame(room, now, 26, 780, 22.4, 55, 420, 30, 0.8));
                arduino(room, now, 22.4, 55, 780.0, 420, true, 0);
            }
            case CO2_ALTO -> {
                send(seatedFrame(room, now, 30, 1450, 21.2, 62, 400, 33, 0.9));
                arduino(room, now, 21.2, 62, 1450.0, 400, true, 0);
            }
            case FALLO_CAMARA -> send(new TelemetrySnapshot(room, now, false, List.of(), 820, 20.3, OUTDOOR_C, 57,
                    410, 22, 0.8));
            case SOBRECUPO -> {
                int crowd = catalog.getRequiredSpace(room).capacity() + 7;
                send(seatedFrame(room, now, crowd, 980, 21.8, 58, 520, crowd, 2.4));
                arduino(room, now, 21.8, 58, 980.0, 520, true, catalog.getRequiredSpace(room).computers());
            }
            case NO_SHOW -> {
                clearSimulationReservations(room, now);
                reserve(room, now.minus(minutes(20)), now.plus(minutes(100)), 30, scenario);
                send(seatedFrame(room, now, 0, 440, 18.6, 54, 60, 1, 0.1));
            }
            case SUBUTILIZADO -> {
                reserve(room, now.minus(minutes(10)), now.plus(minutes(80)), 30, scenario);
                send(seatedFrame(room, now, 6, 520, 19.4, 60, 410, 7, 0.5));
            }
            case POPUP_PARCIALES -> {
                clearSimulationReservations(room, now);
                controls.openPopUpStudyRoom(room, Duration.ofHours(2));
                send(seatedFrame(room, now, 9, 560, 19.8, 58, 450, 10, 0.4));
            }
            case LUCES_ENCENDIDAS -> idleRoom(room, now, minutes(8), true, 0);
            case EQUIPOS_ENCENDIDOS -> idleRoom(room, now, minutes(12), true, catalog.getRequiredSpace(room).computers());
            case OVERRIDE_DOCENTE -> controls.manualOverride(room, minutes(45));
            case MANTENIMIENTO -> controls.setMaintenance(room, true);
            case ACAPARAMIENTO_BIBLIOTECA -> libraryHoarding(room, now);
            case REINICIAR -> {
                for (CampusSpace space : catalog.listSpaces(null)) {
                    clearSimulationReservations(space.spaceId(), now);
                    controls.reset(space.spaceId());
                }
                return new SimulationResult(scenario, queries.spaces(), latestTrace());
            }
        }
        return new SimulationResult(scenario, List.of(queries.space(room)), latestTrace());
    }

    /**
     * The camera sees an empty room that has been empty for {@code idle}; then the Arduino reports the
     * lights (and computers) on, which crosses the idle threshold and triggers the shutdown order.
     */
    private void idleRoom(String spaceId, Instant now, Duration idle, boolean lightsOn, int devicesOn) {
        send(seatedFrame(spaceId, now, 0, 430, 18.9, 52, lightsOn ? 380 : 40, 0, devicesOn * 0.08));
        twins.findTwin(spaceId).ifPresent(twin -> twins.saveTwin(twin.withEmptySince(now.minus(idle))));
        arduino(spaceId, now, 18.9, 52, 430.0, lightsOn ? 380 : 40, lightsOn, devicesOn);
    }

    /**
     * Table 1 has belongings unattended for 16 minutes (released), table 2 for 6 minutes (alert),
     * tables 3–5 have students and the rest are free; more students walk along the aisle.
     */
    private void libraryHoarding(String spaceId, Instant now) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        List<StudyZoneRoi> desks = SeatRegions.of(space, twins);
        if (desks.size() < 5) {
            throw new IllegalStateException("La biblioteca necesita al menos 5 puestos con ROI para la simulación");
        }
        SpaceTwinState twin = twins.findTwin(spaceId).orElseGet(() -> SpaceTwinState.initial(space, now));
        twins.saveTwin(twin.withDesks(List.of(
                new DeskObservation(desks.get(0).zoneId(), ZoneDeskState.ALERTA_ACAPARAMIENTO, 0, 2, now.minus(minutes(16)), now),
                new DeskObservation(desks.get(1).zoneId(), ZoneDeskState.ALERTA_ACAPARAMIENTO, 0, 1, now.minus(minutes(6)), now))));

        List<TrackedDetection> detections = new ArrayList<>();
        long track = 100;
        detections.add(at(track++, DetectedObjectClass.LAPTOP, centroid(desks.get(0)), 0.06, 0.05));
        detections.add(at(track++, DetectedObjectClass.BACKPACK, offset(centroid(desks.get(0)), 0.04, 0.02), 0.05, 0.06));
        detections.add(at(track++, DetectedObjectClass.BOOK, centroid(desks.get(1)), 0.04, 0.03));
        for (int desk = 2; desk <= 4; desk++) {
            detections.add(at(track++, DetectedObjectClass.PERSON, centroid(desks.get(desk)), PERSON_WIDTH, PERSON_HEIGHT));
            detections.add(at(track++, DetectedObjectClass.LAPTOP, offset(centroid(desks.get(desk)), 0.03, 0.03), 0.05, 0.04));
        }
        detections.addAll(aislePeople(30, track));
        send(new TelemetrySnapshot(spaceId, now, true, detections, 760, 19.6, OUTDOOR_C, 52, 450, 40, 2.1));
        arduino(spaceId, now, 19.6, 52, 760.0, 450, true, 6);
    }

    private void send(TelemetrySnapshot snapshot) {
        pipeline.process(snapshot, INBOUND);
    }

    private void arduino(String spaceId, Instant at, double temperatureC, double humidityPct, Double co2Ppm,
                         double lux, boolean lightsOn, int devicesOn) {
        environment.processEnvironment(new EnvironmentReading(spaceId, at, temperatureC, humidityPct, co2Ppm, lux,
                lightsOn, devicesOn), ARDUINO);
    }

    private void reserve(String spaceId, Instant start, Instant end, int attendees, SimulationScenario scenario) {
        clearSimulationReservations(spaceId, start, end);
        reservations.create(new CreateReservationCommand(spaceId, start, end, attendees, RESERVATION_PREFIX + scenario.name()));
    }

    private void clearSimulationReservations(String spaceId, Instant now) {
        clearSimulationReservations(spaceId, now.minus(Duration.ofHours(13)), now.plus(Duration.ofHours(13)));
    }

    private void clearSimulationReservations(String spaceId, Instant from, Instant to) {
        for (Reservation reservation : reservations.list(spaceId, from, to)) {
            if (reservation.reference().startsWith(RESERVATION_PREFIX)) {
                reservations.cancel(reservation.reservationId());
            }
        }
    }

    private ArchitectureTrace latestTrace() {
        List<ArchitectureTrace> latest = queries.recentTraces(1);
        return latest.isEmpty() ? null : latest.get(0);
    }

    /**
     * Camera frame of a room: the first {@code people} go to seats spread over the room (one person per
     * seat region); anyone beyond the seats stands in the front band, outside every seat.
     */
    private TelemetrySnapshot seatedFrame(String spaceId, Instant at, int people, double co2, double indoorC,
                                          double humidity, double lux, int wifi, double powerKw) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        List<StudyZoneRoi> seats = SeatRegions.of(space, twins);
        List<TrackedDetection> detections = new ArrayList<>(people);
        int seated = Math.min(people, seats.size());
        for (int i = 0; i < seated; i++) {
            // Spread the occupied seats: every k-th seat, so a half-full room looks half full everywhere.
            int index = (int) Math.floor(i * (double) seats.size() / seated);
            detections.add(at(i + 1, DetectedObjectClass.PERSON, centroid(seats.get(index)), PERSON_WIDTH, PERSON_HEIGHT));
        }
        detections.addAll(aislePeople(people - seated, seated + 1));
        return new TelemetrySnapshot(spaceId, at, true, detections, co2, indoorC, OUTDOOR_C, humidity, lux, wifi, powerKw);
    }

    /** People standing in the front band y ∈ [0.07, 0.25], which never overlaps a seat region. */
    private static List<TrackedDetection> aislePeople(int count, long firstTrackId) {
        List<TrackedDetection> people = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            double x = 0.04 + (i % 12) * 0.078;
            double y = 0.08 + ((i / 12) % 3) * 0.07;
            people.add(at(firstTrackId + i, DetectedObjectClass.PERSON, new NormalizedPoint(x, y), PERSON_WIDTH, PERSON_HEIGHT));
        }
        return people;
    }

    private static TrackedDetection at(long trackId, DetectedObjectClass type, NormalizedPoint center, double width,
                                       double height) {
        double xMin = clamp(center.x() - width / 2);
        double yMin = clamp(center.y() - height / 2);
        double xMax = clamp(center.x() + width / 2);
        double yMax = clamp(center.y() + height / 2);
        double confidence = type == DetectedObjectClass.PERSON ? 0.91 : 0.78;
        return new TrackedDetection(trackId, type, confidence, new BoundingBox(xMin, yMin, xMax, yMax));
    }

    private static NormalizedPoint centroid(StudyZoneRoi roi) {
        double x = roi.polygon().stream().mapToDouble(NormalizedPoint::x).average().orElseThrow();
        double y = roi.polygon().stream().mapToDouble(NormalizedPoint::y).average().orElseThrow();
        return new NormalizedPoint(x, y);
    }

    private static NormalizedPoint offset(NormalizedPoint point, double dx, double dy) {
        return new NormalizedPoint(clamp(point.x() + dx), clamp(point.y() + dy));
    }

    private static double clamp(double value) {
        return Math.min(1, Math.max(0, value));
    }

    private static Duration minutes(long value) {
        return Duration.ofMinutes(value);
    }
}
