package edu.campusnexo.application.twin;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.vision.SeatGrid;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.List;

/**
 * Seat regions of a space: the regions drawn by hand over its camera when there are any (library tables),
 * otherwise one region per seat derived from its seat layout.
 */
public final class SeatRegions {
    private SeatRegions() {
    }

    public static List<StudyZoneRoi> of(CampusSpace space, SpaceRepositoryPort twins) {
        List<StudyZoneRoi> drawn = twins.findRois(space.spaceId());
        return drawn.isEmpty() ? SeatGrid.regions(space) : drawn;
    }
}
