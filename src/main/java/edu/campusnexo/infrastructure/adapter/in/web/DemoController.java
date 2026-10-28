package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
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
 * sensor, so the rest of the dashboard reacts too; with nobody watching it writes nothing.
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
    private final Clock clock;
    private final Duration dayLength;
    private volatile DemoSimulation simulation;
    private volatile Instant lastViewed = Instant.EPOCH;

    DemoController(CampusCatalogUseCase catalog, ProcessSpaceTelemetryUseCase pipeline, Clock clock,
                   @Value("${app.demo.day-length:20m}") Duration dayLength) {
        this.catalog = catalog;
        this.pipeline = pipeline;
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
                pipeline.process(frame(sala, now), NAME);
            } catch (RuntimeException failure) {
                // One room that fails must not stop the others.
                log.warn("Demo: el frame de {} falló: {}", sala.spaceId(), failure.getMessage());
            }
        }
    }

    private DemoSimulation simulation() {
        if (simulation == null) {
            simulation = new DemoSimulation(DemoSimulation.demoRooms(catalog.listSpaces(null), 8), dayLength);
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
