package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.vision.DeskObservation;
import java.time.Instant;
import java.util.List;

/**
 * Digital-twin state of one space after the latest telemetry frame. {@code lastKalmanStep} and
 * {@code lastThermalDecision} are {@code null} until the first frame is processed. Manual controls
 * ({@code overrideUntil}, {@code popUpUntil}, {@code maintenance}) are set by staff and expire on their own.
 * {@code emptySince} is when the room was last seen becoming empty ({@code null} while there are people),
 * and {@code equipmentShutdownAt} when the last idle shutdown was ordered for that empty period.
 */
public record SpaceTwinState(
        String spaceId,
        SpaceStatus status,
        HvacControlState hvacState,
        Instant hvacStateSince,
        OccupancyBelief occupancy,
        KalmanStep lastKalmanStep,
        ThermalDecision lastThermalDecision,
        double indoorTemperatureC,
        double outdoorTemperatureC,
        double co2Ppm,
        double humidityPct,
        boolean cameraOnline,
        List<DeskObservation> desks,
        double energySavedKwh,
        Instant overrideUntil,
        Instant popUpUntil,
        boolean maintenance,
        Instant updatedAt,
        RoomEquipment equipment,
        RoomCondition condition,
        Instant emptySince,
        Instant equipmentShutdownAt) {

    public SpaceTwinState {
        requireText(spaceId, "spaceId");
        requireNonNull(status, "status");
        requireNonNull(hvacState, "hvacState");
        requireNonNull(hvacStateSince, "hvacStateSince");
        requireNonNull(occupancy, "occupancy");
        requireNonNull(updatedAt, "updatedAt");
        desks = desks == null ? List.of() : List.copyOf(desks);
        requireNonNull(equipment, "equipment");
        requireNonNull(condition, "condition");
    }

    /** State of a space that has not reported telemetry yet. */
    public static SpaceTwinState initial(CampusSpace space, Instant now) {
        return new SpaceTwinState(space.spaceId(), SpaceStatus.LIBRE, HvacControlState.APAGADO, now,
                OccupancyBelief.uninformed(now, space.capacity()), null, null, space.averageOutsideTempC(),
                space.averageOutsideTempC(), 420, 50, false, List.of(), 0, null, null, false, now,
                RoomEquipment.off(space.computers()), RoomCondition.LIBRE, now, null);
    }

    public boolean overrideActive(Instant now) {
        return overrideUntil != null && now.isBefore(overrideUntil);
    }

    public boolean popUpActive(Instant now) {
        return popUpUntil != null && now.isBefore(popUpUntil);
    }

    public SpaceTwinState withControls(Instant newOverrideUntil, Instant newPopUpUntil, boolean newMaintenance) {
        return new SpaceTwinState(spaceId, status, hvacState, hvacStateSince, occupancy, lastKalmanStep,
                lastThermalDecision, indoorTemperatureC, outdoorTemperatureC, co2Ppm, humidityPct, cameraOnline, desks,
                energySavedKwh, newOverrideUntil, newPopUpUntil, newMaintenance, updatedAt, equipment, condition, emptySince,
                equipmentShutdownAt);
    }

    public SpaceTwinState withDesks(List<DeskObservation> newDesks) {
        return new SpaceTwinState(spaceId, status, hvacState, hvacStateSince, occupancy, lastKalmanStep,
                lastThermalDecision, indoorTemperatureC, outdoorTemperatureC, co2Ppm, humidityPct, cameraOnline, newDesks,
                energySavedKwh, overrideUntil, popUpUntil, maintenance, updatedAt, equipment, condition, emptySince,
                equipmentShutdownAt);
    }

    /** Same state, empty since {@code since} (used to replay a room that has been empty for a while). */
    public SpaceTwinState withEmptySince(Instant since) {
        return new SpaceTwinState(spaceId, status, hvacState, hvacStateSince, occupancy, lastKalmanStep,
                lastThermalDecision, indoorTemperatureC, outdoorTemperatureC, co2Ppm, humidityPct, cameraOnline, desks,
                energySavedKwh, overrideUntil, popUpUntil, maintenance, updatedAt, equipment, condition, since,
                equipmentShutdownAt);
    }
}
