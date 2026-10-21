package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.twin.TwinWriter.Change;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * A reading of the room's Arduino (temperature, humidity, light, lights and computers on) updates the
 * environment of the twin and re-runs the policies with the people last estimated by the camera.
 */
public class ProcessEnvironmentReadingService implements ProcessEnvironmentReadingUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TwinWriter writer;
    private final TwinDecisionEngine engine;
    private final TwinPublisher publisher;

    public ProcessEnvironmentReadingService(Clock clock, CampusCatalogUseCase catalog, TwinWriter writer,
                                            TwinDecisionEngine engine, TwinPublisher publisher) {
        this.clock = clock;
        this.catalog = catalog;
        this.writer = writer;
        this.engine = engine;
        this.publisher = publisher;
    }

    @Override
    public SpaceTwinState processEnvironment(EnvironmentReading reading, String inboundAdapter) {
        CampusSpace space = catalog.getRequiredSpace(reading.spaceId());
        Instant now = reading.capturedAt();
        TwinOutcome outcome = writer.update(space.spaceId(), () -> SpaceTwinState.initial(space, now), previous -> {
            TraceRecorder trace = new TraceRecorder(inboundAdapter, space.spaceId(), clock.instant())
                    .step(HexagonalLayer.INBOUND_ADAPTER, inboundAdapter, String.format(Locale.ROOT,
                            "%.1f °C, %.0f %% HR, luces %s, %d equipos encendidos", reading.temperatureC(),
                            reading.humidityPct(), reading.lightsOn() ? "encendidas" : "apagadas", reading.devicesOn()))
                    .step(HexagonalLayer.INPUT_PORT, "ProcessEnvironmentReadingUseCase", "processEnvironment(EnvironmentReading)")
                    .step(HexagonalLayer.USE_CASE, "ProcessEnvironmentReadingService", "Actualiza el ambiente y recalcula el salón");
            Instant evaluationTime = now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now;
            TwinOutcome decided = engine.decide(space, previous, new TwinDecisionEngine.Inputs(previous.occupancy(),
                    previous.lastKalmanStep(), previous.desks(), reading.temperatureC(), previous.outdoorTemperatureC(),
                    reading.co2Ppm() == null ? previous.co2Ppm() : reading.co2Ppm(), reading.humidityPct(),
                    previous.cameraOnline(), TwinDecisionEngine.equipment(space, reading.lightsOn(),
                    reading.illuminanceLux(), reading.devicesOn()), evaluationTime), trace);
            return new Change<>(decided.state(), decided);
        });
        return publisher.publish(outcome);
    }
}
