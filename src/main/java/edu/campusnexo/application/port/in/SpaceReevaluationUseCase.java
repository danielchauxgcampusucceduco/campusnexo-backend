package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.twin.SpaceTwinState;

/** Recomputes status and HVAC strategy from the last known readings, for example after a manual control change. */
public interface SpaceReevaluationUseCase {
    SpaceTwinState reevaluate(String spaceId, String trigger);
}
