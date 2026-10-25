package edu.campusnexo.domain.port;

import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TelemetryRepository {
    Telemetry save(Telemetry telemetry);

    /**
     * Most recent readings first, older than {@code before} when given (keyset pagination; the key is the
     * space id). {@code spaceId} may be {@code null} for all spaces.
     */
    List<Telemetry> findRecent(String spaceId, ResultLimit limit, PageCursor before);

    default List<Telemetry> findRecent(String spaceId, ResultLimit limit) {
        return findRecent(spaceId, limit, null);
    }

    Optional<Telemetry> findLatest(String spaceId);

    /** Latest reading of every space that has at least one reading. */
    List<Telemetry> findLatestPerSpace();

    Optional<Telemetry> findBySpaceAndRecordedAt(String spaceId, Instant recordedAt);

    /** Readings since {@code from}, ordered by space and time, capped at {@code maxRows}. */
    List<Telemetry> findSince(Instant from, int maxRows);

    long countSince(Instant from);

    long countDistinctSpacesSince(Instant from);
}
