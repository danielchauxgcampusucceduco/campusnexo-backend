package edu.campusnexo.application.simulation;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.trace.ArchitectureTrace;
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
 * (reservations, manual controls, previous desk timers) and then sends synthetic frames through the
 * same {@link ProcessSpaceTelemetryUseCase} that real MQTT telemetry uses, so the demo exercises the
 * production code path. Reservations created here are tagged with the {@value #RESERVATION_PREFIX} prefix.
 */
public class SimulationService implements SimulationUseCase {
    static final String RESERVATION_PREFIX = "SIM-";
    static final String INBOUND = "REST · SimulationController";
    private static final double PERSON_WIDTH = 0.045;
    private static final double PERSON_HEIGHT = 0.07;

    private final Clock clock;
    private final ProcessSpaceTelemetryUseCase pipeline;
    private final TwinControlUseCase controls;
    private final ReservationUseCase reservations;
    private final CampusCatalogUseCase catalog;
    private final SpaceRepositoryPort twins;
    private final TwinQueryUseCase queries;

    public SimulationService(Clock clock, ProcessSpaceTelemetryUseCase pipeline, TwinControlUseCase controls,
                             ReservationUseCase reservations, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                             TwinQueryUseCase queries) {
        this.clock = clock;
        this.pipeline = pipeline;
        this.controls = controls;
        this.reservations = reservations;
        this.catalog = catalog;
        this.twins = twins;
        this.queries = queries;
    }

    @Override
    public SimulationResult run(SimulationScenario scenario) {
        Instant now = clock.instant();
        switch (scenario) {
            case CLASE_EN_CURSO -> {
                reserve("AULA-301", now.minus(minutes(10)), now.plus(minutes(80)), 28, scenario);
                send(frame("AULA-301", now, 26, 780, 22.5, 14.0, 55, 320, 30, 2.2));
            }
            case CO2_ALTO -> send(frame("AULA-301", now, 30, 1450, 22.8, 14.0, 62, 300, 33, 2.4));
            case FALLO_CAMARA -> send(new TelemetrySnapshot("AULA-301", now, false, List.of(), 820, 22.4, 14.0, 57,
                    310, 22, 2.0));
            case NO_SHOW -> {
                clearSimulationReservations("AULA-MAGNA-204", now);
                reserve("AULA-MAGNA-204", now.minus(minutes(20)), now.plus(minutes(100)), 40, scenario);
                send(frame("AULA-MAGNA-204", now, 0, 440, 26.8, 31.8, 70, 900, 1, 4.1));
            }
            case PRE_ENFRIAMIENTO -> {
                clearSimulationReservations("AULA-MAGNA-204", now);
                reserve("AULA-MAGNA-204", now.plus(minutes(20)), now.plus(minutes(110)), 42, scenario);
                send(frame("AULA-MAGNA-204", now, 0, 430, 28.5, 32.0, 74, 1100, 0, 0.3));
            }
            case OVERRIDE_DOCENTE -> controls.manualOverride("AULA-MAGNA-204", minutes(45));
            case SOBRECUPO -> send(frame("LAB-IA-102", now, 31, 900, 23.5, 14.0, 58, 380, 30, 3.6));
            case MANTENIMIENTO -> controls.setMaintenance("LAB-IA-102", true);
            case SUBUTILIZADO -> {
                reserve("AULA-ADM-101", now.minus(minutes(10)), now.plus(minutes(80)), 30, scenario);
                send(frame("AULA-ADM-101", now, 6, 520, 22.0, 22.8, 60, 410, 7, 1.9));
            }
            case POPUP_PARCIALES -> {
                clearSimulationReservations("AULA-ADM-101", now);
                controls.openPopUpStudyRoom("AULA-ADM-101", Duration.ofHours(2));
                send(frame("AULA-ADM-101", now, 9, 560, 22.4, 22.8, 58, 450, 10, 1.1));
            }
            case ACAPARAMIENTO_BIBLIOTECA -> libraryHoarding(now);
            case REINICIAR -> {
                for (CampusSpace space : catalog.listSpaces(null)) {
                    clearSimulationReservations(space.spaceId(), now);
                    controls.reset(space.spaceId());
                }
                return new SimulationResult(scenario, queries.spaces(), latestTrace());
            }
        }
        return new SimulationResult(scenario, List.of(queries.space(scenario.spaceId())), latestTrace());
    }

    /**
     * Desk 1 has belongings unattended for 16 minutes (released), desk 2 for 6 minutes (alert),
     * desks 3–5 have students and the rest are free; more students walk along the aisle.
     */
    private void libraryHoarding(Instant now) {
        String spaceId = "BIB-PISO-2";
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        List<StudyZoneRoi> desks = twins.findRois(spaceId);
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
        detections.addAll(aislePeople(34, track));
        send(new TelemetrySnapshot(spaceId, now, true, detections, 760, 20.5, 14.0, 52, 420, 40, 5.4));
    }

    private void send(TelemetrySnapshot snapshot) {
        pipeline.process(snapshot, INBOUND);
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

    /** Classroom frame: {@code people} person boxes laid out in rows; classrooms have no desk regions. */
    private static TelemetrySnapshot frame(String spaceId, Instant at, int people, double co2, double indoorC,
                                           double outdoorC, double humidity, double lux, int wifi, double powerKw) {
        return new TelemetrySnapshot(spaceId, at, true, aislePeople(people, 1), co2, indoorC, outdoorC, humidity, lux,
                wifi, powerKw);
    }

    /** People laid out in rows inside the band y ∈ [0.04, 0.30], which never overlaps a desk region. */
    private static List<TrackedDetection> aislePeople(int count, long firstTrackId) {
        List<TrackedDetection> people = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double x = 0.04 + (i % 12) * 0.078;
            double y = 0.07 + ((i / 12) % 3) * 0.08;
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
