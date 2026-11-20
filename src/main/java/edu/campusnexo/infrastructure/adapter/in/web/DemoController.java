package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.DisponibilidadUseCase;
import edu.campusnexo.application.port.in.EmulatedDataUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.horario.SesionDeClase;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.SalaVista;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The system demo ({@code DEMO_SIMULATION=true}): a compressed academic week over the timetable rooms, with what each
 * room's camera sees. Nothing is emulated until the staff starts it from Simulación ({@code POST}); from then, while
 * someone watches it, the same frames feed the digital twin as an emulated sensor, so the rest of the dashboard reacts
 * too (availability, alerts), and so do the two cameras of the library. A room where a real camera already reports is
 * left to that camera. Leaving the demo ({@code DELETE}) stops it and erases every emulated figure; so does a restart,
 * which never resumes a demo on its own.
 */
@RestController
@EnableScheduling
@RequestMapping(ApiPaths.BASE + "/demo")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoController {
    static final String NAME = "Demo · " + DemoController.class.getSimpleName();
    private static final Logger log = LoggerFactory.getLogger(DemoController.class);
    private static final Duration WATCHED_FOR = Duration.ofMinutes(10);

    private final CampusCatalogUseCase catalog;
    private final ProcessSpaceTelemetryUseCase pipeline;
    private final TwinQueryUseCase twins;
    private final EmulatedDataUseCase emulated;
    private final DisponibilidadUseCase disponibilidad;
    private final Clock clock;
    private final Duration dayLength;
    private volatile DemoSimulation simulation;
    private volatile Instant lastViewed = Instant.EPOCH;
    /** Started by hand from Simulación; looking at the screen alone never starts it. */
    private volatile boolean active;
    /** Set while someone leaves: a batch under way stops after its current frame instead of holding the exit. */
    private volatile boolean leaving;
    private final Object feeding = new Object();

    DemoController(CampusCatalogUseCase catalog, ProcessSpaceTelemetryUseCase pipeline, TwinQueryUseCase twins,
                   EmulatedDataUseCase emulated, DisponibilidadUseCase disponibilidad, Clock clock, @Value("${app.demo.day-length:20m}") Duration dayLength) {
        this.catalog = catalog;
        this.pipeline = pipeline;
        this.twins = twins;
        this.emulated = emulated;
        this.disponibilidad = disponibilidad;
        this.clock = clock;
        this.dayLength = dayLength;
    }

    @GetMapping
    DemoSimulation.Snapshot snapshot() {
        Instant now = clock.instant();
        if (active) {
            lastViewed = now;
        }
        return simulation().snapshot(now).activa(active);
    }

    /** Starts the demo (staff only): from now on it feeds the twin while someone watches it. */
    @PostMapping
    DemoSimulation.Snapshot start() {
        active = true;
        return snapshot();
    }

    /** A restart does not resume the demo: whatever it had emulated before is erased, so a sign-in shows real data only. */
    @EventListener(ApplicationReadyEvent.class)
    void eraseLeftovers() {
        List<String> cleaned = emulated.purge();
        if (!cleaned.isEmpty()) {
            log.info("Demo: al arrancar se borraron los datos emulados de {} espacios", cleaned.size());
        }
    }

    /** Leaves the demo: it stops, and the rooms it fed start again with no emulated data left. */
    @DeleteMapping
    Map<String, List<String>> leave() {
        active = false;
        lastViewed = Instant.EPOCH;
        leaving = true;
        // Waits only for the frame in progress, so no emulated frame lands after the purge.
        synchronized (feeding) {
            try {
                simulation = null;
                return Map.of("salonesLimpiados", emulated.purge());
            } finally {
                leaving = false;
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.demo.feed-interval:15s}", initialDelayString = "${app.demo.feed-interval:15s}")
    void feedTwin() {
        synchronized (feeding) {
            feed();
        }
    }

    private void feed() {
        Instant now = clock.instant();
        if (!active || Duration.between(lastViewed, now).compareTo(WATCHED_FOR) > 0) {
            return;
        }
        Set<String> real = withRealCamera();
        for (SalaVista sala : simulation().snapshot(now).salones()) {
            if (leaving) {
                return;
            }
            if (real.contains(sala.spaceId())) {
                continue;
            }
            try {
                // Each frame carries the instant it is sent, as a camera would: one instant for the whole batch made
                // the last rooms look seconds late on the dashboard (QA-2) while the batch went through the pipeline.
                pipeline.process(frame(sala, clock.instant()), NAME);
            } catch (RuntimeException failure) {
                // One room that fails must not stop the others.
                log.warn("Demo: el frame de {} falló: {}", sala.spaceId(), failure.getMessage());
            }
        }
        for (String library : List.of(DemoSimulation.BIBLIOTECA, DemoSimulation.BIBLIOTECA_ESQUINA)) {
            if (leaving) {
                return;
            }
            if (real.contains(library)) {
                continue;
            }
            try {
                SpaceTwinView view = twins.space(library);
                pipeline.process(libraryFrame(view.space(), view.rois(), now), NAME);
            } catch (ResourceNotFoundException withoutThatCamera) {
                // A database without V13 and V14 has no such library camera to feed.
            } catch (RuntimeException failure) {
                log.warn("Demo: el frame de {} falló: {}", library, failure.getMessage());
            }
        }
    }

    /** Rooms whose last frame came from a real camera: the demo must not overwrite them. */
    private Set<String> withRealCamera() {
        return twins.spaces().stream()
                .filter(view -> view.state().sensing().lastCameraFrameAt() != null && !view.state().sensing().emulated())
                .map(view -> view.space().spaceId())
                .collect(Collectors.toSet());
    }

    /** A camera of the library: students and belongings at its tables. */
    static TelemetrySnapshot libraryFrame(CampusSpace library, List<StudyZoneRoi> tables, Instant now) {
        return new TelemetrySnapshot(library.spaceId(), now, true, DemoSimulation.mesas(tables, now), null, true);
    }

    private DemoSimulation simulation() {
        if (simulation == null) {
            List<SesionDeClase> horario = disponibilidad.horario();
            simulation = new DemoSimulation(DemoSimulation.demoRooms(catalog.listSpaces(null), horario), horario, dayLength);
        }
        return simulation;
    }

    /**
     * What the room's camera sends: the people as detections on the floor, or nothing with the camera offline while
     * its signal is lost.
     */
    static TelemetrySnapshot frame(SalaVista sala, Instant now) {
        DemoSimulation.Ahora ahora = sala.ahora();
        List<TrackedDetection> people = new ArrayList<>();
        long track = 1;
        for (DemoSimulation.PersonaVista p : ahora.personasVistas()) {
            people.add(new TrackedDetection(track++, DetectedObjectClass.PERSON, 0.9, new BoundingBox(
                    Math.max(0, p.x() - 0.02), Math.max(0, p.y() - 0.04), Math.min(1, p.x() + 0.02), Math.min(1, p.y() + 0.04))));
        }
        return new TelemetrySnapshot(sala.spaceId(), now, ahora.camaraEnLinea(),
                ahora.camaraEnLinea() ? people : List.of(), null, true);
    }
}
