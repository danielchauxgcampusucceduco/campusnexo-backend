package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Duration;

/** Manual controls exercised by staff over the digital twin. */
public interface TwinControlUseCase {
    /** Opens a free room as a pop-up study room during exam weeks. */
    SpaceTwinState openPopUpStudyRoom(String spaceId, Duration duration);

    SpaceTwinState setMaintenance(String spaceId, boolean maintenance);

    /** Clears the controls and desks of the space: it starts again as if its camera had never reported. */
    SpaceTwinState reset(String spaceId);
}
