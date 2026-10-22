package edu.campusnexo.domain.port;

import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.List;
import java.util.Map;

/** Desk regions drawn by hand over a camera image (library tables). Rooms without drawn regions use their seat grid. */
public interface SeatRegionRepository {
    List<StudyZoneRoi> findRois(String spaceId);

    /** All drawn regions, by space, in one query. */
    Map<String, List<StudyZoneRoi>> findAllRois();
}
