package edu.campusnexo.support;

import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class InMemoryTelemetryRepository implements TelemetryRepository {
    private final List<Telemetry> rows = new ArrayList<>();

    public List<Telemetry> rows() {
        return rows;
    }

    @Override
    public Telemetry save(Telemetry telemetry) {
        rows.add(telemetry);
        return telemetry;
    }

    @Override
    public List<Telemetry> findRecent(String spaceId, ResultLimit limit, PageCursor before) {
        Comparator<Telemetry> newestFirst = Comparator.comparing(Telemetry::recordedAt)
                .thenComparing(Telemetry::spaceId).reversed();
        return rows.stream().filter(row -> spaceId == null || row.spaceId().equals(spaceId))
                .filter(row -> before == null || row.recordedAt().isBefore(before.at())
                        || row.recordedAt().equals(before.at()) && row.spaceId().compareTo(before.key()) < 0)
                .sorted(newestFirst).limit(limit.value()).toList();
    }

    @Override
    public Optional<Telemetry> findLatest(String spaceId) {
        return findRecent(spaceId, ResultLimit.of(1)).stream().findFirst();
    }

    @Override
    public List<Telemetry> findLatestPerSpace() {
        return List.copyOf(rows.stream().collect(Collectors.toMap(Telemetry::spaceId, Function.identity(),
                (a, b) -> a.recordedAt().isAfter(b.recordedAt()) ? a : b)).values());
    }

    @Override
    public Optional<Telemetry> findBySpaceAndRecordedAt(String spaceId, Instant recordedAt) {
        return rows.stream().filter(row -> row.spaceId().equals(spaceId) && row.recordedAt().equals(recordedAt))
                .findFirst();
    }

    @Override
    public List<Telemetry> findSince(Instant from, int maxRows) {
        return rows.stream().filter(row -> !row.recordedAt().isBefore(from))
                .sorted(Comparator.comparing(Telemetry::spaceId).thenComparing(Telemetry::recordedAt))
                .limit(maxRows).toList();
    }

    @Override
    public long countSince(Instant from) {
        return rows.stream().filter(row -> !row.recordedAt().isBefore(from)).count();
    }

    @Override
    public long countDistinctSpacesSince(Instant from) {
        return rows.stream().filter(row -> !row.recordedAt().isBefore(from)).map(Telemetry::spaceId).distinct().count();
    }
}
