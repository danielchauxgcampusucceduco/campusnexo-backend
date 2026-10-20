package edu.campusnexo.domain.port;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AlertRepository {
    Alert save(Alert alert);

    /** Most recent first; {@code spaceId} and {@code type} are optional filters. */
    List<Alert> findRecent(String spaceId, AlertType type, ResultLimit limit);

    boolean existsSince(String spaceId, AlertType type, Instant from);

    Map<AlertType, Long> countByTypeSince(Instant from);
}
