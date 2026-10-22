package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.vision.DeskObservation;
import java.time.Instant;
import java.util.List;

/**
 * Digital-twin state of one space, composed of value objects: what the climate unit is doing
 * ({@link HvacSetting}), the occupancy belief of the Kalman filter, the last readings ({@link Environment}),
 * the devices' reporting state ({@link Sensing}), the desks, the people's controls ({@link ManualControls}),
 * the lights and computers, and the empty-room timers ({@link RoomTimers}).
 *
 * <p>{@code lastKalmanStep} and {@code lastThermalDecision} explain the latest decision and are {@code null}
 * until the first frame. New states are built with {@link #toBuilder()}, so no caller passes fifteen
 * values in a fixed order.
 */
public record SpaceTwinState(
        String spaceId,
        SpaceStatus status,
        HvacSetting hvac,
        OccupancyBelief occupancy,
        KalmanStep lastKalmanStep,
        ThermalDecision lastThermalDecision,
        Environment environment,
        Sensing sensing,
        List<DeskObservation> desks,
        double energySavedKwh,
        ManualControls controls,
        RoomEquipment equipment,
        RoomCondition condition,
        RoomTimers timers,
        Instant updatedAt) {

    public SpaceTwinState {
        requireText(spaceId, "spaceId");
        requireNonNull(status, "status");
        requireNonNull(hvac, "hvac");
        requireNonNull(occupancy, "occupancy");
        requireNonNull(environment, "environment");
        requireNonNull(sensing, "sensing");
        desks = desks == null ? List.of() : List.copyOf(desks);
        requireNonNull(controls, "controls");
        requireNonNull(equipment, "equipment");
        requireNonNull(condition, "condition");
        requireNonNull(timers, "timers");
        requireNonNull(updatedAt, "updatedAt");
    }

    /** State of a space that has not reported telemetry yet. */
    public static SpaceTwinState initial(CampusSpace space, Instant now) {
        HvacSetting hvac = space.hvacInstalled() ? HvacSetting.off(now)
                : new HvacSetting(HvacControlState.SIN_CLIMATIZACION, HvacMode.APAGADO, 21.0, now,
                "El espacio no tiene equipo de climatización");
        return new SpaceTwinState(space.spaceId(), SpaceStatus.LIBRE, hvac,
                OccupancyBelief.uninformed(now, space.capacity()), null, null,
                new Environment(space.averageOutsideTempC(), space.averageOutsideTempC(), 420, 50), Sensing.none(),
                List.of(), 0, ManualControls.none(), RoomEquipment.off(space.computers()), RoomCondition.LIBRE,
                new RoomTimers(null, null), now);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    // Read-only shortcuts used by the policies and the contracts.

    public HvacControlState hvacState() {
        return hvac.state();
    }

    public Instant hvacStateSince() {
        return hvac.since();
    }

    public HvacMode hvacMode() {
        return hvac.mode();
    }

    public double indoorTemperatureC() {
        return environment.indoorTemperatureC();
    }

    public double outdoorTemperatureC() {
        return environment.outdoorTemperatureC();
    }

    public double co2Ppm() {
        return environment.co2Ppm();
    }

    public double humidityPct() {
        return environment.humidityPct();
    }

    public boolean cameraOnline() {
        return sensing.cameraOnline();
    }

    public Instant overrideUntil() {
        return controls.overrideUntil();
    }

    public Instant popUpUntil() {
        return controls.popUpUntil();
    }

    public boolean maintenance() {
        return controls.maintenance();
    }

    public boolean overrideActive(Instant now) {
        return controls.overrideActive(now);
    }

    public boolean popUpActive(Instant now) {
        return controls.popUpActive(now);
    }

    public Instant emptySince() {
        return timers.emptySince();
    }

    public Instant equipmentShutdownAt() {
        return timers.equipmentShutdownAt();
    }

    public SpaceTwinState withControls(ManualControls newControls) {
        return toBuilder().controls(newControls).build();
    }

    /** Builder: copies a state and changes only the parts that are set. */
    public static final class Builder {
        private String spaceId;
        private SpaceStatus status;
        private HvacSetting hvac;
        private OccupancyBelief occupancy;
        private KalmanStep lastKalmanStep;
        private ThermalDecision lastThermalDecision;
        private Environment environment;
        private Sensing sensing;
        private List<DeskObservation> desks;
        private double energySavedKwh;
        private ManualControls controls;
        private RoomEquipment equipment;
        private RoomCondition condition;
        private RoomTimers timers;
        private Instant updatedAt;

        private Builder(SpaceTwinState base) {
            spaceId = base.spaceId;
            status = base.status;
            hvac = base.hvac;
            occupancy = base.occupancy;
            lastKalmanStep = base.lastKalmanStep;
            lastThermalDecision = base.lastThermalDecision;
            environment = base.environment;
            sensing = base.sensing;
            desks = base.desks;
            energySavedKwh = base.energySavedKwh;
            controls = base.controls;
            equipment = base.equipment;
            condition = base.condition;
            timers = base.timers;
            updatedAt = base.updatedAt;
        }

        public Builder status(SpaceStatus value) {
            status = value;
            return this;
        }

        public Builder hvac(HvacSetting value) {
            hvac = value;
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

        public Builder lastThermalDecision(ThermalDecision value) {
            lastThermalDecision = value;
            return this;
        }

        public Builder environment(Environment value) {
            environment = value;
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

        public Builder energySavedKwh(double value) {
            energySavedKwh = value;
            return this;
        }

        public Builder controls(ManualControls value) {
            controls = value;
            return this;
        }

        public Builder equipment(RoomEquipment value) {
            equipment = value;
            return this;
        }

        public Builder condition(RoomCondition value) {
            condition = value;
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
            return new SpaceTwinState(spaceId, status, hvac, occupancy, lastKalmanStep, lastThermalDecision,
                    environment, sensing, desks, energySavedKwh, controls, equipment, condition, timers, updatedAt);
        }
    }
}
