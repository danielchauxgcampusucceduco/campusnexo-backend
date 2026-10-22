package edu.campusnexo.support;

import edu.campusnexo.domain.port.SeatRegionRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.ConcurrentTwinUpdateException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySpaceRepositoryPort implements SpaceRepositoryPort, SeatRegionRepository {
    private final Map<String, StoredTwin> twins = new LinkedHashMap<>();
    /** Number of writes rejected because the caller saw an old revision. */
    public int conflicts;
    private final Map<String, List<StudyZoneRoi>> rois = new LinkedHashMap<>();
    /** Number of desk-region queries, to check that hot paths read them in batch or from the cache. */
    public int roiQueries;

    /** Same 4 × 2 desk layout seeded by Flyway V10 for BIB-PISO-2. */
    public static List<StudyZoneRoi> libraryDesks() {
        return libraryDesks("BIB-PISO-2", "BIB2-M");
    }

    /** The 4 × 2 table layout of a library hall, e.g. {@code 201-B} with zone ids {@code 201B-M01…}. */
    public static List<StudyZoneRoi> libraryDesks(String spaceId, String zonePrefix) {
        List<StudyZoneRoi> desks = new ArrayList<>();
        double[][] columns = {{0.06, 0.26}, {0.29, 0.49}, {0.52, 0.72}, {0.75, 0.95}};
        double[][] rows = {{0.35, 0.58}, {0.65, 0.88}};
        int number = 1;
        for (double[] row : rows) {
            for (double[] column : columns) {
                desks.add(new StudyZoneRoi(String.format("%s%02d", zonePrefix, number), spaceId, "Mesa " + number, List.of(
                        new NormalizedPoint(column[0], row[0]), new NormalizedPoint(column[1], row[0]),
                        new NormalizedPoint(column[1], row[1]), new NormalizedPoint(column[0], row[1]))));
                number++;
            }
        }
        return desks;
    }

    public InMemorySpaceRepositoryPort withRois(String spaceId, List<StudyZoneRoi> zones) {
        rois.put(spaceId, List.copyOf(zones));
        return this;
    }

    @Override
    public synchronized Optional<StoredTwin> findStoredTwin(String spaceId) {
        return Optional.ofNullable(twins.get(spaceId));
    }

    @Override
    public synchronized List<SpaceTwinState> findAllTwins() {
        return twins.values().stream().map(StoredTwin::state).toList();
    }

    @Override
    public synchronized StoredTwin saveTwin(SpaceTwinState state, long expectedRevision) {
        StoredTwin current = twins.get(state.spaceId());
        long currentRevision = current == null ? StoredTwin.NEW : current.revision();
        if (currentRevision != expectedRevision) {
            conflicts++;
            throw new ConcurrentTwinUpdateException(state.spaceId());
        }
        StoredTwin stored = new StoredTwin(state, currentRevision + 1);
        twins.put(state.spaceId(), stored);
        return stored;
    }

    /** Test helper: stores a twin without the optimistic check, as another backend instance would. */
    public synchronized void overwrite(SpaceTwinState state) {
        StoredTwin current = twins.get(state.spaceId());
        twins.put(state.spaceId(), new StoredTwin(state, current == null ? 0 : current.revision() + 1));
    }

    @Override
    public synchronized List<StudyZoneRoi> findRois(String spaceId) {
        roiQueries++;
        return rois.getOrDefault(spaceId, List.of());
    }

    @Override
    public synchronized Map<String, List<StudyZoneRoi>> findAllRois() {
        roiQueries++;
        return Map.copyOf(rois);
    }
}
