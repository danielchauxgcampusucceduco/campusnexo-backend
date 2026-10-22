package edu.campusnexo.domain.port;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AlertRepository {
    Alert save(Alert alert);

    /**
     * Most recent first, older than {@code before} when given (keyset pagination; the key is the alert
     * id). {@code spaceId} and {@code type} are optional filters.
     */
    List<Alert> findRecent(String spaceId, AlertType type, ResultLimit limit, PageCursor before);

    default List<Alert> findRecent(String spaceId, AlertType type, ResultLimit limit) {
        return findRecent(spaceId, type, limit, null);
    }

    boolean existsSince(String spaceId, AlertType type, Instant from);

    /**
     * Stores {@code alert} unless an alert of the same type was raised for the space since {@code since}.
     * Implementations must make check and insert atomic, also across backend instances (PostgreSQL: a
     * transaction-scoped advisory lock per space and type), so concurrent observers never duplicate an alert.
     *
     * @return whether the alert was stored
     */
    boolean saveUnlessRaisedSince(Alert alert, Instant since);

    Map<AlertType, Long> countByTypeSince(Instant from);
}
