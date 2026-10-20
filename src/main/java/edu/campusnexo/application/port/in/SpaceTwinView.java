package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.List;

/** Space, its twin state, its current reservation ({@code null} if none) and its desk regions. */
public record SpaceTwinView(CampusSpace space, SpaceTwinState state, Reservation activeReservation,
                            List<StudyZoneRoi> rois) {
}
