package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.application.twin.TwinWriter.Change;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.telemetry.ReadingAdmission;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.Environment;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * A reading of the room's Arduino (temperature, humidity, light, lights and computers on) updates the
 * environment of the twin and re-runs the policies with the people last estimated by the camera. Like camera
 * frames, readings are validated before anything changes and applied once, in capture order.
 */
public class ProcessEnvironmentReadingService implements ProcessEnvironmentReadingUseCase {
    static final String SOURCE = "arduino";

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final ReservationRepository reservations;
    private final ReadingAdmission admission;
    private final TwinWriter writer;
    private final TwinDecisionEngine engine;
    private final TwinPublisher publisher;
    private final OperationalMetrics metrics;

    public ProcessEnvironmentReadingService(Clock clock, CampusCatalogUseCase catalog, ReservationRepository reservations,
                                            ReadingAdmission admission, TwinWriter writer, TwinDecisionEngine engine,
                                            TwinPublisher publisher, OperationalMetrics metrics) {
        this.clock = clock;
        this.catalog = catalog;
        this.reservations = reservations;
        this.admission = admission;
        this.writer = writer;
        this.engine = engine;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Override
    public SpaceTwinState processEnvironment(EnvironmentReading reading, String inboundAdapter) {
        CampusSpace space = catalog.getRequiredSpace(reading.spaceId());
        Instant now = reading.capturedAt();
        admission.check(now, clock.instant());
        ReservationContext context = ReservationContext.lookup(reservations, space.spaceId(), now);

        TwinOutcome outcome = writer.update(space.spaceId(), () -> SpaceTwinState.initial(space, now), previous -> {
            if (!previous.sensing().acceptsEnvironmentReading(now)) {
                return Change.unchanged(TwinOutcome.unchanged(previous));
            }
            TwinOutcome decided = decide(space, previous, reading, context, inboundAdapter);
            return new Change<>(decided.state(), decided);
        });
        if (!outcome.changed()) {
            metrics.readingDiscarded(SOURCE, outcome.state().sensing().lastEnvironmentAt().equals(now)
                    ? "duplicada" : "fuera_de_orden");
            return outcome.state();
        }
        return publisher.publish(outcome);
    }

    private TwinOutcome decide(CampusSpace space, SpaceTwinState previous, EnvironmentReading reading,
                               ReservationContext context, String inboundAdapter) {
        TraceRecorder trace = new TraceRecorder(inboundAdapter, space.spaceId(), clock.instant())
                .step(HexagonalLayer.INBOUND_ADAPTER, inboundAdapter, String.format(Locale.ROOT,
                        "%.1f °C, %.0f %% HR, luces %s, %d equipos encendidos", reading.temperatureC(),
                        reading.humidityPct(), reading.lightsOn() ? "encendidas" : "apagadas", reading.devicesOn()))
                .step(HexagonalLayer.INPUT_PORT, ProcessEnvironmentReadingUseCase.class,
                        "processEnvironment(EnvironmentReading)")
                .step(HexagonalLayer.USE_CASE, ProcessEnvironmentReadingService.class,
                        "Actualiza el ambiente y recalcula el salón");
        Instant capturedAt = reading.capturedAt();
        Instant evaluationTime = capturedAt.isBefore(previous.updatedAt()) ? previous.updatedAt() : capturedAt;
        Environment environment = new Environment(reading.temperatureC(), previous.outdoorTemperatureC(),
                reading.co2Ppm() == null ? previous.co2Ppm() : reading.co2Ppm(), reading.humidityPct());
        return engine.decide(space, previous, new TwinDecisionEngine.Inputs(previous.occupancy(),
                previous.lastKalmanStep(), previous.desks(), previous.presence(), environment,
                previous.sensing().environmentReading(capturedAt),
                TwinDecisionEngine.equipment(space, reading.lightsOn(), reading.illuminanceLux(), reading.devicesOn()),
                evaluationTime), context, trace);
    }
}
