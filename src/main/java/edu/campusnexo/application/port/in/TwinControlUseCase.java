package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Duration;

/** Manual controls exercised by staff over the digital twin. */
public interface TwinControlUseCase {
    /** A teacher takes control of the HVAC for {@code duration}. */
    SpaceTwinState manualOverride(String spaceId, Duration duration);

    /** Opens a free room as a pop-up study room during exam weeks. */
    SpaceTwinState openPopUpStudyRoom(String spaceId, Duration duration);

    SpaceTwinState setMaintenance(String spaceId, boolean maintenance);

    /** Clears controls, desks and accumulated savings of the space. */
    SpaceTwinState reset(String spaceId);
}
