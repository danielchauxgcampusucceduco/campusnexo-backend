package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.catalog.SpaceType;
import java.util.List;

/** Answers "where is there room right now?", for example library floors and study areas. */
public interface LiveOccupancyUseCase {
    /** Spaces with free seats first; {@code type} is an optional filter. */
    List<SpaceOccupancyStatus> current(SpaceType type);
}
