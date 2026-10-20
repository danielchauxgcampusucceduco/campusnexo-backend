package edu.campusnexo.domain.port;

import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.List;
import java.util.Optional;

/** Persistence of the digital twin and of the desk regions drawn over each camera. */
public interface SpaceRepositoryPort {
    Optional<SpaceTwinState> findTwin(String spaceId);

    List<SpaceTwinState> findAllTwins();

    SpaceTwinState saveTwin(SpaceTwinState state);

    List<StudyZoneRoi> findRois(String spaceId);
}
