package edu.campusnexo.application.caching;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Proxy: same interface as the catalog repository, but answers from memory. The catalog only changes with
 * a migration, yet every frame, reading and dashboard request looked a room up in PostgreSQL; with the proxy
 * the whole catalog is read once per time to live.
 */
public final class CachingSpaceCatalogRepository implements SpaceCatalogRepository {
    private final ExpiringValue<Snapshot> snapshot;

    public CachingSpaceCatalogRepository(SpaceCatalogRepository subject, Clock clock, Duration ttl) {
        this.snapshot = new ExpiringValue<>(clock, ttl, () -> Snapshot.of(subject.findAll()));
    }

    @Override
    public List<CampusSpace> findAll() {
        return snapshot.get().spaces();
    }

    @Override
    public Optional<CampusSpace> findById(String spaceId) {
        return Optional.ofNullable(snapshot.get().byId().get(spaceId));
    }

    public void invalidate() {
        snapshot.invalidate();
    }

    private record Snapshot(List<CampusSpace> spaces, Map<String, CampusSpace> byId) {
        static Snapshot of(List<CampusSpace> spaces) {
            return new Snapshot(List.copyOf(spaces), spaces.stream()
                    .collect(Collectors.toUnmodifiableMap(CampusSpace::spaceId, Function.identity())));
        }
    }
}
