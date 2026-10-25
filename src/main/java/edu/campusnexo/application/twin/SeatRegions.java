package edu.campusnexo.application.twin;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SeatRegionRepository;
import edu.campusnexo.domain.vision.SeatGrid;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.List;
import java.util.Map;

/**
 * Seat regions of a space: the regions drawn by hand over its camera when there are any (library tables),
 * otherwise one region per seat derived from its seat layout.
 */
public final class SeatRegions {
    private final SeatRegionRepository drawn;

    public SeatRegions(SeatRegionRepository drawn) {
        this.drawn = drawn;
    }

    public List<StudyZoneRoi> of(CampusSpace space) {
        return choose(space, drawn.findRois(space.spaceId()));
    }

    /** Regions of several spaces with a single repository call. */
    public Map<String, List<StudyZoneRoi>> ofAll(List<CampusSpace> spaces) {
        Map<String, List<StudyZoneRoi>> all = drawn.findAllRois();
        return spaces.stream().collect(java.util.stream.Collectors.toMap(CampusSpace::spaceId,
                space -> choose(space, all.getOrDefault(space.spaceId(), List.of())), (a, b) -> a,
                java.util.LinkedHashMap::new));
    }

    private static List<StudyZoneRoi> choose(CampusSpace space, List<StudyZoneRoi> drawnRegions) {
        return drawnRegions.isEmpty() ? SeatGrid.regions(space) : drawnRegions;
    }
}
