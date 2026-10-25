package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.SeatGrid;
import edu.campusnexo.domain.vision.TrackedDetection;
import java.util.List;

/**
 * People in the room by role, recognized by where they are and never by who they are: a person in the front
 * band ({@link SeatGrid#inTeacherZone}) counts as teacher, a person inside a seat region as student. People in
 * the aisles or at the door are in the fused count but in neither role. Rooms without seats have no roles.
 */
public record Presence(int teachers, int students) {
    public Presence {
        require(teachers >= 0 && students >= 0, "Los conteos por rol no pueden ser negativos");
    }

    public static Presence none() {
        return new Presence(0, 0);
    }

    /** Roles in one camera frame, given the desks already evaluated for it. */
    public static Presence of(List<TrackedDetection> detections, List<DeskObservation> desks) {
        if (desks.isEmpty()) {
            return none();
        }
        int teachers = (int) detections.stream()
                .filter(d -> d.objectClass() == DetectedObjectClass.PERSON)
                .filter(d -> SeatGrid.inTeacherZone(d.box().center()))
                .count();
        int students = desks.stream().mapToInt(DeskObservation::peopleInside).sum();
        return new Presence(teachers, students);
    }

    public boolean teacherPresent() {
        return teachers > 0;
    }
}
