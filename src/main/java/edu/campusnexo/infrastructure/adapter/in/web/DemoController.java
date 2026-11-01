package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.SalaVista;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The system demo ({@code DEMO_SIMULATION=true}): a compressed academic day over the campus rooms, with what a
 * normal and an infrared camera see. While someone watches it, the same frames feed the digital twin as an emulated
 * sensor, so the rest of the dashboard reacts too, and so do the two cameras of the library (the reading room and the
 * corner one over the silent-study tables); with nobody watching it writes nothing.
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
    private final Clock clock;
    private final Duration dayLength;
    private volatile DemoSimulation simulation;
    private volatile Instant lastViewed = Instant.EPOCH;

    DemoController(CampusCatalogUseCase catalog, ProcessSpaceTelemetryUseCase pipeline, TwinQueryUseCase twins,
                   Clock clock, @Value("${app.demo.day-length:20m}") Duration dayLength) {
        this.catalog = catalog;
        this.pipeline = pipeline;
        this.twins = twins;
        this.clock = clock;
        this.dayLength = dayLength;
    }

    @GetMapping
    DemoSimulation.Snapshot snapshot() {
        lastViewed = clock.instant();
        return simulation().snapshot(clock.instant());
    }

    @Scheduled(fixedDelayString = "${app.demo.feed-interval:15s}", initialDelayString = "${app.demo.feed-interval:15s}")
    void feedTwin() {
        Instant now = clock.instant();
        if (Duration.between(lastViewed, now).compareTo(WATCHED_FOR) > 0) {
            return;
        }
        for (SalaVista sala : simulation().snapshot(now).salones()) {
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

    /** A camera of the library: students and belongings at its tables, lights on, nothing else. */
    static TelemetrySnapshot libraryFrame(CampusSpace library, List<StudyZoneRoi> tables, Instant now) {
        List<TrackedDetection> seen = DemoSimulation.mesas(tables, now);
        int people = (int) seen.stream().filter(d -> d.objectClass() == DetectedObjectClass.PERSON).count();
        return new TelemetrySnapshot(library.spaceId(), now, true, seen, 430 + 900.0 * people / library.capacity(),
                18.5, null, 55, 450, people, 0.3, true, 0, true);
    }

    private DemoSimulation simulation() {
        if (simulation == null) {
            List<DemoSimulation.Sesion> horario = DemoSimulation.horario();
            simulation = new DemoSimulation(DemoSimulation.demoRooms(catalog.listSpaces(null), horario), horario, dayLength);
        }
        return simulation;
    }

    /** The fused count (infrared) as person detections, plus the room's lights and devices. */
    static TelemetrySnapshot frame(SalaVista sala, Instant now) {
        DemoSimulation.Ahora ahora = sala.ahora();
        List<TrackedDetection> people = new ArrayList<>();
        long track = 1;
        for (DemoSimulation.PersonaVista p : ahora.personasVistas()) {
            people.add(new TrackedDetection(track++, DetectedObjectClass.PERSON, 0.9, new BoundingBox(
                    Math.max(0, p.x() - 0.02), Math.max(0, p.y() - 0.04), Math.min(1, p.x() + 0.02), Math.min(1, p.y() + 0.04))));
        }
        return new TelemetrySnapshot(sala.spaceId(), now, true, people, ahora.co2Ppm(), ahora.temperaturaC(), null, 60,
                ahora.luces() ? 420 : 12, ahora.personas(), ahora.potenciaKw(), ahora.luces(),
                ahora.dispositivosEncendidos(), true);
    }
}
