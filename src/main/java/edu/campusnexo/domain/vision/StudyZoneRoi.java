package edu.campusnexo.domain.vision;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Region of interest drawn over the camera image for one study desk. Detections are assigned to the
 * desk when the center of their bounding box falls inside the polygon.
 */
public record StudyZoneRoi(String zoneId, String spaceId, String label, List<NormalizedPoint> polygon) {

    public StudyZoneRoi {
        requireText(zoneId, "zoneId");
        requireText(spaceId, "spaceId");
        requireText(label, "label");
        requireNonNull(polygon, "polygon");
        require(polygon.size() >= 3, "Un ROI necesita al menos 3 vértices");
        polygon = List.copyOf(polygon);
    }

    /**
     * Ray casting (even-odd rule): a horizontal ray from the point crosses the polygon boundary an odd
     * number of times when the point is inside. Each edge (i, j) is counted when it straddles the ray's
     * y coordinate and the intersection lies to the right of the point.
     */
    public boolean containsPoint(NormalizedPoint point) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            NormalizedPoint a = polygon.get(i);
            NormalizedPoint b = polygon.get(j);
            boolean straddles = (a.y() > point.y()) != (b.y() > point.y());
            if (straddles) {
                double intersectionX = (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x();
                if (point.x() < intersectionX) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /**
     * Anti-hoarding rule for one desk:
     * <ul>
     *   <li>a person inside → {@code OCUPADO_VERIFICADO} (timer reset);</li>
     *   <li>belongings without a person → {@code ALERTA_ACAPARAMIENTO}, starting the timer the first time;</li>
     *   <li>the same situation for at least {@code abandonThreshold} → {@code LIBERADO_POR_ABANDONO};</li>
     *   <li>nothing inside → {@code DISPONIBLE}.</li>
     * </ul>
     *
     * @param previous last observation of this desk, or {@code null} if there is none
     */
    public DeskObservation evaluateDeskGhosting(List<TrackedDetection> detections, DeskObservation previous,
                                                Instant now, Duration abandonThreshold) {
        int people = 0;
        int belongings = 0;
        for (TrackedDetection detection : detections) {
            if (containsPoint(detection.box().center())) {
                if (detection.objectClass().isBelonging()) {
                    belongings++;
                } else {
                    people++;
                }
            }
        }
        if (people > 0) {
            return new DeskObservation(zoneId, ZoneDeskState.OCUPADO_VERIFICADO, people, belongings, null, now);
        }
        if (belongings == 0) {
            return new DeskObservation(zoneId, ZoneDeskState.DISPONIBLE, 0, 0, null, now);
        }
        Instant since = previous != null && previous.unattendedSince() != null && !previous.unattendedSince().isAfter(now)
                ? previous.unattendedSince()
                : now;
        ZoneDeskState state = Duration.between(since, now).compareTo(abandonThreshold) >= 0
                ? ZoneDeskState.LIBERADO_POR_ABANDONO
                : ZoneDeskState.ALERTA_ACAPARAMIENTO;
        return new DeskObservation(zoneId, state, 0, belongings, since, now);
    }
}
