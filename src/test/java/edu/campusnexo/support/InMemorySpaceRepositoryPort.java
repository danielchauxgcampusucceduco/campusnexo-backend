package edu.campusnexo.support;

import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySpaceRepositoryPort implements SpaceRepositoryPort {
    private final Map<String, SpaceTwinState> twins = new LinkedHashMap<>();
    private final Map<String, List<StudyZoneRoi>> rois = new LinkedHashMap<>();

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
    public Optional<SpaceTwinState> findTwin(String spaceId) {
        return Optional.ofNullable(twins.get(spaceId));
    }

    @Override
    public List<SpaceTwinState> findAllTwins() {
        return List.copyOf(twins.values());
    }

    @Override
    public SpaceTwinState saveTwin(SpaceTwinState state) {
        twins.put(state.spaceId(), state);
        return state;
    }

    @Override
    public List<StudyZoneRoi> findRois(String spaceId) {
        return rois.getOrDefault(spaceId, List.of());
    }
}
