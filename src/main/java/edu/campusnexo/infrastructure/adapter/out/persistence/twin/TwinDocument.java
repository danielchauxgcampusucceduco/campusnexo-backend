package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
import edu.campusnexo.domain.twin.KalmanMeasurement;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.ManualControls;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.Presence;
import edu.campusnexo.domain.twin.RoomTimers;
import edu.campusnexo.domain.twin.Sensing;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.ZoneDeskState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Storage format of {@link SpaceTwinState}. Kept separate from the domain records so that refactoring the
 * domain does not silently change the persisted JSON; {@link #VERSION} identifies the format and the JSON
 * stays flat. A document of another version is rejected instead of being guessed (version 1 carried the climate
 * control, removed with migration V16).
 */
record TwinDocument(
        int version,
        String spaceId,
        SpaceStatus status,
        Belief occupancy,
        Kalman kalman,
        boolean cameraOnline,
        Instant lastCameraFrameAt,
        boolean emulated,
        List<Desk> desks,
        int teachersPresent,
        int studentsPresent,
        Instant popUpUntil,
        boolean maintenance,
        Instant updatedAt,
        Instant emptySince,
        UUID attendedReservation) {

    static final int VERSION = 2;

    static TwinDocument from(SpaceTwinState state) {
        return new TwinDocument(VERSION, state.spaceId(), state.status(), Belief.from(state.occupancy()),
                Kalman.from(state.lastKalmanStep()), state.cameraOnline(), state.sensing().lastCameraFrameAt(),
                state.sensing().emulated(), state.desks().stream().map(Desk::from).toList(), state.presence().teachers(),
                state.presence().students(), state.popUpUntil(), state.maintenance(), state.updatedAt(),
                state.emptySince(), state.timers().attendedReservation());
    }

    SpaceTwinState toDomain() {
        if (version != VERSION) {
            throw new IllegalStateException("Versión de documento de gemelo no soportada: " + version);
        }
        return new SpaceTwinState(spaceId, status, occupancy.toDomain(), kalman == null ? null : kalman.toDomain(),
                new Sensing(cameraOnline, lastCameraFrameAt, emulated), desks.stream().map(Desk::toDomain).toList(),
                new Presence(teachersPresent, studentsPresent), new ManualControls(popUpUntil, maintenance),
                new RoomTimers(emptySince, attendedReservation), updatedAt);
    }

    record Belief(double estimate, double variance, Instant at) {
        static Belief from(OccupancyBelief belief) {
            return new Belief(belief.estimate(), belief.variance(), belief.at());
        }

        OccupancyBelief toDomain() {
            return new OccupancyBelief(estimate, variance, at);
        }
    }

    record Measurement(MeasurementSource source, double value, double variance, double gain) {
    }

    record Kalman(Belief previous, double elapsedMinutes, double predictedVariance, List<Measurement> measurements,
                  Belief posterior) {
        static Kalman from(KalmanStep step) {
            if (step == null) {
                return null;
            }
            return new Kalman(Belief.from(step.previous()), step.elapsedMinutes(), step.predictedVariance(),
                    step.measurements().stream().map(m -> new Measurement(m.source(), m.value(), m.variance(), m.gain()))
                            .toList(),
                    Belief.from(step.posterior()));
        }

        KalmanStep toDomain() {
            return new KalmanStep(previous.toDomain(), elapsedMinutes, predictedVariance,
                    measurements.stream().map(m -> new KalmanMeasurement(m.source(), m.value(), m.variance(), m.gain()))
                            .toList(),
                    posterior.toDomain());
        }
    }

    record Desk(String zoneId, ZoneDeskState state, int peopleInside, int belongingsInside, Instant unattendedSince,
                Instant evaluatedAt) {
        static Desk from(DeskObservation desk) {
            return new Desk(desk.zoneId(), desk.state(), desk.peopleInside(), desk.belongingsInside(),
                    desk.unattendedSince(), desk.evaluatedAt());
        }

        DeskObservation toDomain() {
            return new DeskObservation(zoneId, state, peopleInside, belongingsInside, unattendedSince, evaluatedAt);
        }
    }
}
