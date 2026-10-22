package edu.campusnexo.domain.port;

import edu.campusnexo.domain.twin.ConcurrentTwinUpdateException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.List;
import java.util.Optional;

/**
 * Persistence of the digital twin.
 *
 * <p>Writes are optimistic: every stored twin carries a revision, and {@link #saveTwin} only succeeds when
 * the caller saw the latest one. Two streams of the same room (camera and Arduino, or two backend
 * instances) therefore cannot overwrite each other's state silently.
 */
public interface SpaceRepositoryPort {
    Optional<StoredTwin> findStoredTwin(String spaceId);

    default Optional<SpaceTwinState> findTwin(String spaceId) {
        return findStoredTwin(spaceId).map(StoredTwin::state);
    }

    List<SpaceTwinState> findAllTwins();

    /**
     * Stores {@code state} if the current revision is still {@code expectedRevision}
     * ({@link StoredTwin#NEW} when the room has no twin yet).
     *
     * @throws ConcurrentTwinUpdateException if another writer stored the room first
     */
    StoredTwin saveTwin(SpaceTwinState state, long expectedRevision);

    /** A twin as stored, with the revision a writer must present to replace it. */
    record StoredTwin(SpaceTwinState state, long revision) {
        public static final long NEW = -1;
    }
}
