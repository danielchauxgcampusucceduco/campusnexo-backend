package edu.campusnexo.application.twin;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.Presence;
import edu.campusnexo.domain.twin.RoomTimers;
import edu.campusnexo.domain.twin.Sensing;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceStatusPolicy;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.DeskObservation;
import java.time.Instant;
import java.util.List;

/**
 * Applies the domain policies to one room: whether it is free, booked, in use, overcrowded or out of service, and
 * since when it is empty. It reads nothing and has no side effects (the reservations arrive in a
 * {@link ReservationContext}), so {@link TwinWriter} can recompute it after a concurrent write.
 */
public final class TwinDecisionEngine {
    private final SpaceStatusPolicy statusPolicy;

    public TwinDecisionEngine(SpaceStatusPolicy statusPolicy) {
        this.statusPolicy = statusPolicy;
    }

    /** What the room looks like now, as the camera saw it or carried over from the previous twin. */
    record Inputs(OccupancyBelief belief, KalmanStep kalmanStep, List<DeskObservation> desks, Presence presence,
                  Sensing sensing, Instant now) {
    }

    TwinOutcome decide(CampusSpace space, SpaceTwinState previous, Inputs in, ReservationContext reservations,
                       TraceRecorder trace) {
        Instant now = in.now();
        int people = in.belief().people();
        Reservation active = reservations.active();
        boolean attended = active != null && (people > 0 || active.checkedIn()
                || active.reservationId().equals(previous.timers().attendedReservation()));
        SpaceStatus status = statusPolicy.classify(previous.maintenance(), previous.popUpActive(now), people,
                space.capacity(), active, attended, now);
        trace.step(HexagonalLayer.DOMAIN_SERVICE, SpaceStatusPolicy.class,
                people + "/" + space.capacity() + " personas" + (active == null ? "" : ", reserva " + active.reference())
                        + " → " + status);

        SpaceTwinState state = previous.toBuilder()
                .status(status)
                .occupancy(in.belief())
                .lastKalmanStep(in.kalmanStep())
                .sensing(in.sensing())
                .desks(in.desks())
                .presence(in.presence())
                .timers(new RoomTimers(emptySince(people, previous.emptySince(), now),
                        attended ? active.reservationId() : null))
                .updatedAt(now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now)
                .build();
        return new TwinOutcome(previous, state, trace);
    }

    /** Start of the current empty period: the earliest observation of it, even if frames arrive late. */
    private static Instant emptySince(int people, Instant previous, Instant now) {
        if (people > 0) {
            return null;
        }
        return previous == null || previous.isAfter(now) ? now : previous;
    }
}
