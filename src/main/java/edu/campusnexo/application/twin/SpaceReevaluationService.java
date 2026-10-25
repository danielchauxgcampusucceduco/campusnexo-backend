package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SpaceReevaluationUseCase;
import edu.campusnexo.application.twin.TwinWriter.Change;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Instant;
import java.util.function.UnaryOperator;

/**
 * Decides a room again with its last readings, optionally after changing it (a manual control or a reset):
 * the change and the new decision are one write, so nobody ever sees a control applied without its effect.
 */
public class SpaceReevaluationService implements SpaceReevaluationUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final ReservationRepository reservations;
    private final TwinWriter writer;
    private final TwinDecisionEngine engine;
    private final TwinPublisher publisher;

    public SpaceReevaluationService(Clock clock, CampusCatalogUseCase catalog, ReservationRepository reservations,
                                    TwinWriter writer, TwinDecisionEngine engine, TwinPublisher publisher) {
        this.clock = clock;
        this.catalog = catalog;
        this.reservations = reservations;
        this.writer = writer;
        this.engine = engine;
        this.publisher = publisher;
    }

    @Override
    public SpaceTwinState reevaluate(String spaceId, String trigger) {
        return reevaluate(spaceId, trigger, UnaryOperator.identity());
    }

    /** Applies {@code change} to the room and decides it again, in the same write. */
    SpaceTwinState reevaluate(String spaceId, String trigger, UnaryOperator<SpaceTwinState> change) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        Instant now = clock.instant();
        ReservationContext context = ReservationContext.lookup(reservations, spaceId, now);
        TwinOutcome outcome = writer.update(spaceId, () -> SpaceTwinState.initial(space, now), stored -> {
            SpaceTwinState previous = change.apply(stored);
            TraceRecorder trace = new TraceRecorder(trigger, spaceId, now)
                    .step(HexagonalLayer.INBOUND_ADAPTER, trigger, "Cambio de control manual")
                    .step(HexagonalLayer.INPUT_PORT, SpaceReevaluationUseCase.class, "reevaluate(" + spaceId + ")")
                    .step(HexagonalLayer.USE_CASE, SpaceReevaluationService.class, "Recalcula estado y estrategia HVAC");
            Instant evaluationTime = now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now;
            TwinOutcome decided = engine.decide(space, previous, new TwinDecisionEngine.Inputs(previous.occupancy(),
                    previous.lastKalmanStep(), previous.desks(), previous.presence(), previous.environment(),
                    previous.sensing(),
                    previous.equipment(), evaluationTime), context, trace);
            return new Change<>(decided.state(), new TwinOutcome(stored, decided.state(), decided.equipmentCommand(),
                    decided.trace()));
        });
        return publisher.publish(outcome);
    }
}
