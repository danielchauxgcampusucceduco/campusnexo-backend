package edu.campusnexo.application.twin;

import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.port.SpaceRepositoryPort.StoredTwin;
import edu.campusnexo.domain.twin.ConcurrentTwinUpdateException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The only way the application writes a twin: read → decide → compare-and-set, serialized per room.
 *
 * <p>Inside one instance, a lock per room makes the camera, the Arduino and manual controls of the same
 * room take turns. Across instances, the repository rejects a write based on a stale revision and the
 * whole decision is recomputed from the fresh state (up to {@value #MAX_ATTEMPTS} times). The decision
 * function must therefore be free of side effects: commands and notifications happen after the write.
 */
public final class TwinWriter {
    static final int MAX_ATTEMPTS = 3;

    private final SpaceRepositoryPort twins;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public TwinWriter(SpaceRepositoryPort twins) {
        this.twins = twins;
    }

    public <R> R update(String spaceId, Supplier<SpaceTwinState> initial, Function<SpaceTwinState, Change<R>> decision) {
        ReentrantLock lock = locks.computeIfAbsent(spaceId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            for (int attempt = 1; ; attempt++) {
                Optional<StoredTwin> stored = twins.findStoredTwin(spaceId);
                SpaceTwinState previous = stored.map(StoredTwin::state).orElseGet(initial);
                Change<R> change = decision.apply(previous);
                try {
                    twins.saveTwin(change.state(), stored.map(StoredTwin::revision).orElse(StoredTwin.NEW));
                    return change.result();
                } catch (ConcurrentTwinUpdateException conflict) {
                    if (attempt == MAX_ATTEMPTS) {
                        throw conflict;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** Replaces the twin with {@code change(previous)} and returns the stored state. */
    public SpaceTwinState replace(String spaceId, Supplier<SpaceTwinState> initial,
                                  Function<SpaceTwinState, SpaceTwinState> change) {
        return update(spaceId, initial, previous -> {
            SpaceTwinState next = change.apply(previous);
            return new Change<>(next, next);
        });
    }

    /** The state to store and what the caller needs back from the decision. */
    public record Change<R>(SpaceTwinState state, R result) {
    }
}
