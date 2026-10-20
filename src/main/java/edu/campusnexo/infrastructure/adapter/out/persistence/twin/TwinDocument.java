package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.KalmanMeasurement;
import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.RoomEquipment;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.ThermalDecision;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.ZoneDeskState;
import java.time.Instant;
import java.util.List;

/**
 * Storage format of {@link SpaceTwinState}. Kept separate from the domain records so that refactoring the
 * domain does not silently change the persisted JSON; {@link #VERSION} identifies the format. Version 1
 * documents (before rooms reported lights and computers) are still read: their equipment is unknown and
 * starts switched off.
 */
record TwinDocument(
        int version,
        String spaceId,
        SpaceStatus status,
        HvacControlState hvacState,
        Instant hvacStateSince,
        Belief occupancy,
        Kalman kalman,
        Thermal thermal,
        double indoorTemperatureC,
        double outdoorTemperatureC,
        double co2Ppm,
        double humidityPct,
        boolean cameraOnline,
        List<Desk> desks,
        double energySavedKwh,
        Instant overrideUntil,
        Instant popUpUntil,
        boolean maintenance,
        Instant updatedAt,
        Equipment equipment,
        RoomCondition condition,
        Instant emptySince,
        Instant equipmentShutdownAt) {

    static final int VERSION = 2;

    static TwinDocument from(SpaceTwinState state) {
        return new TwinDocument(VERSION, state.spaceId(), state.status(), state.hvacState(), state.hvacStateSince(),
                Belief.from(state.occupancy()), Kalman.from(state.lastKalmanStep()), Thermal.from(state.lastThermalDecision()),
                state.indoorTemperatureC(), state.outdoorTemperatureC(), state.co2Ppm(), state.humidityPct(),
                state.cameraOnline(), state.desks().stream().map(Desk::from).toList(), state.energySavedKwh(),
                state.overrideUntil(), state.popUpUntil(), state.maintenance(), state.updatedAt(),
                Equipment.from(state.equipment()), state.condition(), state.emptySince(), state.equipmentShutdownAt());
    }

    SpaceTwinState toDomain() {
        if (version != 1 && version != VERSION) {
            throw new IllegalStateException("Versión de documento de gemelo no soportada: " + version);
        }
        OccupancyBelief belief = occupancy.toDomain();
        return new SpaceTwinState(spaceId, status, hvacState, hvacStateSince, belief,
                kalman == null ? null : kalman.toDomain(), thermal == null ? null : thermal.toDomain(), indoorTemperatureC,
                outdoorTemperatureC, co2Ppm, humidityPct, cameraOnline,
                desks == null ? List.of() : desks.stream().map(Desk::toDomain).toList(), energySavedKwh, overrideUntil,
                popUpUntil, maintenance, updatedAt,
                equipment == null ? RoomEquipment.off(0) : equipment.toDomain(),
                condition == null ? (belief.people() > 0 ? RoomCondition.OCUPADO : RoomCondition.LIBRE) : condition,
                emptySince, equipmentShutdownAt);
    }

    record Equipment(boolean lightsOn, double illuminanceLux, int devicesOn, int devicesTotal) {
        static Equipment from(RoomEquipment equipment) {
            return new Equipment(equipment.lightsOn(), equipment.illuminanceLux(), equipment.devicesOn(),
                    equipment.devicesTotal());
        }

        RoomEquipment toDomain() {
            return new RoomEquipment(lightsOn, illuminanceLux, devicesOn, devicesTotal);
        }
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

    record Thermal(HvacControlState state, double setpointC, int horizonMinutes, int stepMinutes,
                   List<Double> trajectoryC, String explanation) {
        static Thermal from(ThermalDecision decision) {
            if (decision == null) {
                return null;
            }
            return new Thermal(decision.state(), decision.setpointC(), decision.horizonMinutes(), decision.stepMinutes(),
                    decision.freeRunningTrajectoryC(), decision.explanation());
        }

        ThermalDecision toDomain() {
            return new ThermalDecision(state, setpointC, horizonMinutes, stepMinutes, trajectoryC, explanation);
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
