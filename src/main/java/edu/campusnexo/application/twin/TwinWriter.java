package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.application.port.out.UnitOfWork;
import edu.campusnexo.domain.port.SpaceRepositoryPort.StoredTwin;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.ConcurrentTwinUpdateException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The only way the application writes a twin: read → decide → compare-and-set, serialized per room, each
 * attempt in one transaction.
 *
 * <p>Inside one instance, a lock per room makes the camera, the Arduino and manual controls of the same
 * room take turns. Across instances, the repository rejects a write based on a stale revision and the
 * whole decision is recomputed from the fresh state (up to {@value #MAX_ATTEMPTS} times). Whatever must be
 * stored together with the twin (the reading of the frame) runs in the same transaction through
 * {@link Change#alsoInTransaction()}. The decision function must be free of side effects: commands and
 * notifications happen after the write.
 */
public final class TwinWriter {
    static final int MAX_ATTEMPTS = 3;

    private final SpaceRepositoryPort twins;
    private final UnitOfWork unitOfWork;
    private final OperationalMetrics metrics;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public TwinWriter(SpaceRepositoryPort twins, UnitOfWork unitOfWork, OperationalMetrics metrics) {
        this.twins = twins;
        this.unitOfWork = unitOfWork;
        this.metrics = metrics;
    }

    /** In-memory twin without transactions (tests and tools). */
    public TwinWriter(SpaceRepositoryPort twins) {
        this(twins, UnitOfWork.direct(), OperationalMetrics.none());
    }

    /**
     * Applies {@code decision} to the room's current twin and stores its state. Callers pass existing space
     * ids only (validated against the catalog), so the lock map is bounded by the size of the campus.
     */
    public <R> R update(String spaceId, Supplier<SpaceTwinState> initial, Function<SpaceTwinState, Change<R>> decision) {
        ReentrantLock lock = locks.computeIfAbsent(spaceId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    return unitOfWork.inTransaction(() -> decideAndStore(spaceId, initial, decision));
                } catch (ConcurrentTwinUpdateException conflict) {
                    metrics.twinWriteConflict();
                    if (attempt == MAX_ATTEMPTS) {
                        throw conflict;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private <R> R decideAndStore(String spaceId, Supplier<SpaceTwinState> initial, Function<SpaceTwinState, Change<R>> decision) {
        Optional<StoredTwin> stored = twins.findStoredTwin(spaceId);
        SpaceTwinState previous = stored.map(StoredTwin::state).orElseGet(initial);
        Change<R> change = decision.apply(previous);
        if (change.state() != null) {
            twins.saveTwin(change.state(), stored.map(StoredTwin::revision).orElse(StoredTwin.NEW));
            change.alsoInTransaction().run();
        }
        return change.result();
    }

    /** Replaces the twin with {@code change(previous)} and returns the stored state. */
    public SpaceTwinState replace(String spaceId, Supplier<SpaceTwinState> initial,
                                  Function<SpaceTwinState, SpaceTwinState> change) {
        return update(spaceId, initial, previous -> {
            SpaceTwinState next = change.apply(previous);
            return new Change<>(next, next);
        });
    }

    /**
     * The state to store ({@code null}: leave the twin as it is), what the caller needs back from the
     * decision, and what must be committed together with the new state.
     */
    public record Change<R>(SpaceTwinState state, R result, Runnable alsoInTransaction) {
        public Change(SpaceTwinState state, R result) {
            this(state, result, () -> { });
        }

        public static <R> Change<R> unchanged(R result) {
            return new Change<>(null, result, () -> { });
        }
    }
}
