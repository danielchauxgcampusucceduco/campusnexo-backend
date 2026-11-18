package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.vision.DeskObservation;
import java.time.Instant;
import java.util.List;

/**
 * Digital-twin state of one space, composed of value objects: whether it is free, booked or in use
 * ({@link SpaceStatus}), the occupancy belief of the Kalman filter, the camera's reporting state ({@link Sensing}),
 * the desks and who is in them by role ({@link Presence}), the staff's controls ({@link ManualControls}) and the
 * empty-room timer ({@link RoomTimers}).
 *
 * <p>{@code lastKalmanStep} explains the latest estimate and is {@code null} until the first frame. New states are
 * built with {@link #toBuilder()}, so no caller passes ten values in a fixed order.
 */
public record SpaceTwinState(
        String spaceId,
        SpaceStatus status,
        OccupancyBelief occupancy,
        KalmanStep lastKalmanStep,
        Sensing sensing,
        List<DeskObservation> desks,
        Presence presence,
        ManualControls controls,
        RoomTimers timers,
        Instant updatedAt) {

    public SpaceTwinState {
        requireText(spaceId, "spaceId");
        requireNonNull(status, "status");
        requireNonNull(occupancy, "occupancy");
        requireNonNull(sensing, "sensing");
        desks = desks == null ? List.of() : List.copyOf(desks);
        presence = presence == null ? Presence.none() : presence;
        requireNonNull(controls, "controls");
        requireNonNull(timers, "timers");
        requireNonNull(updatedAt, "updatedAt");
    }

    /** State of a space whose camera has not reported yet. */
    public static SpaceTwinState initial(CampusSpace space, Instant now) {
        return new SpaceTwinState(space.spaceId(), SpaceStatus.LIBRE, OccupancyBelief.uninformed(now, space.capacity()),
                null, Sensing.none(), List.of(), Presence.none(), ManualControls.none(), new RoomTimers(null, null), now);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    // Read-only shortcuts used by the policies and the contracts.

    public boolean cameraOnline() {
        return sensing.cameraOnline();
    }

    public Instant popUpUntil() {
        return controls.popUpUntil();
    }

    public boolean maintenance() {
        return controls.maintenance();
    }

    public boolean popUpActive(Instant now) {
        return controls.popUpActive(now);
    }

    public Instant emptySince() {
        return timers.emptySince();
    }

    public SpaceTwinState withControls(ManualControls newControls) {
        return toBuilder().controls(newControls).build();
    }

    /** Builder: copies a state and changes only the parts that are set. */
    public static final class Builder {
        private String spaceId;
        private SpaceStatus status;
        private OccupancyBelief occupancy;
        private KalmanStep lastKalmanStep;
        private Sensing sensing;
        private List<DeskObservation> desks;
        private Presence presence;
        private ManualControls controls;
        private RoomTimers timers;
        private Instant updatedAt;

        private Builder(SpaceTwinState base) {
            spaceId = base.spaceId;
            status = base.status;
            occupancy = base.occupancy;
            lastKalmanStep = base.lastKalmanStep;
            sensing = base.sensing;
            desks = base.desks;
            presence = base.presence;
            controls = base.controls;
            timers = base.timers;
            updatedAt = base.updatedAt;
        }

        public Builder status(SpaceStatus value) {
            status = value;
            return this;
        }

        public Builder occupancy(OccupancyBelief value) {
            occupancy = value;
            return this;
        }

        public Builder lastKalmanStep(KalmanStep value) {
            lastKalmanStep = value;
            return this;
        }

        public Builder sensing(Sensing value) {
            sensing = value;
            return this;
        }

        public Builder desks(List<DeskObservation> value) {
            desks = value;
            return this;
        }

        public Builder presence(Presence value) {
            presence = value;
            return this;
        }

        public Builder controls(ManualControls value) {
            controls = value;
            return this;
        }

        public Builder timers(RoomTimers value) {
            timers = value;
            return this;
        }

        public Builder updatedAt(Instant value) {
            updatedAt = value;
            return this;
        }

        public SpaceTwinState build() {
            return new SpaceTwinState(spaceId, status, occupancy, lastKalmanStep, sensing, desks, presence, controls,
                    timers, updatedAt);
        }
    }
}
