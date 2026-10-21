package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SpaceReevaluationUseCase;
import edu.campusnexo.application.twin.TwinWriter.Change;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Instant;

/** After a manual control (override, pop-up, maintenance, reset) the room is decided again with its last readings. */
public class SpaceReevaluationService implements SpaceReevaluationUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final TwinWriter writer;
    private final TwinDecisionEngine engine;
    private final TwinPublisher publisher;

    public SpaceReevaluationService(Clock clock, CampusCatalogUseCase catalog, TwinWriter writer,
                                    TwinDecisionEngine engine, TwinPublisher publisher) {
        this.clock = clock;
        this.catalog = catalog;
        this.writer = writer;
        this.engine = engine;
        this.publisher = publisher;
    }

    @Override
    public SpaceTwinState reevaluate(String spaceId, String trigger) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        TwinOutcome outcome = writer.update(spaceId, () -> SpaceTwinState.initial(space, now), previous -> {
            TraceRecorder trace = new TraceRecorder(trigger, spaceId, now)
                    .step(HexagonalLayer.INBOUND_ADAPTER, trigger, "Cambio de control manual")
                    .step(HexagonalLayer.INPUT_PORT, "SpaceReevaluationUseCase", "reevaluate(" + spaceId + ")")
                    .step(HexagonalLayer.USE_CASE, "SpaceReevaluationService", "Recalcula estado y estrategia HVAC");
            Instant evaluationTime = now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now;
            TwinOutcome decided = engine.decide(space, previous, new TwinDecisionEngine.Inputs(previous.occupancy(),
                    previous.lastKalmanStep(), previous.desks(), previous.indoorTemperatureC(),
                    previous.outdoorTemperatureC(), previous.co2Ppm(), previous.humidityPct(), previous.cameraOnline(),
                    previous.equipment(), evaluationTime), trace);
            return new Change<>(decided.state(), decided);
        });
        return publisher.publish(outcome);
    }
}
