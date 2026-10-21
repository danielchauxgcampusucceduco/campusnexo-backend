package edu.campusnexo.support;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.AlertRepository;
import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class InMemoryAlertRepository implements AlertRepository {
    private final List<Alert> rows = new ArrayList<>();

    public List<Alert> rows() {
        return rows;
    }

    @Override
    public Alert save(Alert alert) {
        rows.add(alert);
        return alert;
    }

    @Override
    public List<Alert> findRecent(String spaceId, AlertType type, ResultLimit limit, PageCursor before) {
        Comparator<Alert> newestFirst = Comparator.comparing(Alert::detectedAt)
                .thenComparing(row -> row.alertId().toString()).reversed();
        return rows.stream()
                .filter(row -> spaceId == null || row.spaceId().equals(spaceId))
                .filter(row -> type == null || row.type() == type)
                .filter(row -> before == null || row.detectedAt().isBefore(before.at())
                        || row.detectedAt().equals(before.at()) && row.alertId().toString().compareTo(before.key()) < 0)
                .sorted(newestFirst).limit(limit.value()).toList();
    }

    @Override
    public boolean existsSince(String spaceId, AlertType type, Instant from) {
        return rows.stream().anyMatch(row -> row.spaceId().equals(spaceId) && row.type() == type
                && !row.detectedAt().isBefore(from));
    }

    @Override
    public Map<AlertType, Long> countByTypeSince(Instant from) {
        return rows.stream().filter(row -> !row.detectedAt().isBefore(from))
                .collect(Collectors.groupingBy(Alert::type, Collectors.counting()));
    }
}
