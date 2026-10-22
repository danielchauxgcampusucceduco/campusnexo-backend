package edu.campusnexo.application.caching;

import edu.campusnexo.domain.port.SeatRegionRepository;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Proxy for the desk regions drawn over each camera: static data that the camera pipeline needed on every
 * frame. All regions are read in one query per time to live.
 */
public final class CachingSeatRegionRepository implements SeatRegionRepository {
    private final ExpiringValue<Map<String, List<StudyZoneRoi>>> regions;

    public CachingSeatRegionRepository(SeatRegionRepository subject, Clock clock, Duration ttl) {
        this.regions = new ExpiringValue<>(clock, ttl, () -> Map.copyOf(subject.findAllRois()));
    }

    @Override
    public List<StudyZoneRoi> findRois(String spaceId) {
        return regions.get().getOrDefault(spaceId, List.of());
    }

    @Override
    public Map<String, List<StudyZoneRoi>> findAllRois() {
        return regions.get();
    }
}
