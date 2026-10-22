package edu.campusnexo.application.twin;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.climate.policy.ClimateFamilies;
import edu.campusnexo.domain.climate.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.telemetry.MeasurementLimits;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.Environment;
import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.HvacSetting;
import edu.campusnexo.domain.twin.IdleEquipmentShutdownPolicy;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.RoomConditionPolicy;
import edu.campusnexo.domain.twin.RoomEquipment;
import edu.campusnexo.domain.twin.RoomTimers;
import edu.campusnexo.domain.twin.Sensing;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceStatusPolicy;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.ThermalContext;
import edu.campusnexo.domain.twin.ThermalDecision;
import edu.campusnexo.domain.twin.ThermalInertiaMpcDomainService;
import edu.campusnexo.domain.twin.hvac.HvacRequest;
import edu.campusnexo.domain.twin.hvac.HvacResolution;
import edu.campusnexo.domain.twin.hvac.HvacSettingChain;
import edu.campusnexo.domain.vision.DeskObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Applies the domain policies to one room, in order: reservation and capacity status, the predictive
 * thermal strategy over the comfort band of the room's climate family, the HVAC rule chain, energy saved,
 * overall room condition and the idle shutdown order. It reads nothing and has no side effects (the
 * reservations arrive in a {@link ReservationContext}), so {@link TwinWriter} can recompute it after a
 * concurrent write.
 */
public final class TwinDecisionEngine {
    private final SpaceStatusPolicy statusPolicy;
    private final ThermalInertiaMpcDomainService thermal;
    private final ClimateFamilies families;
    private final HvacSettingChain hvacRules;
    private final EnergyAccountingPolicy energy;
    private final RoomConditionPolicy conditionPolicy;
    private final IdleEquipmentShutdownPolicy shutdownPolicy;

    public TwinDecisionEngine(SpaceStatusPolicy statusPolicy, ThermalInertiaMpcDomainService thermal,
                              ClimateFamilies families, HvacSettingChain hvacRules, EnergyAccountingPolicy energy,
                              RoomConditionPolicy conditionPolicy, IdleEquipmentShutdownPolicy shutdownPolicy) {
        this.statusPolicy = statusPolicy;
        this.thermal = thermal;
        this.families = families;
        this.hvacRules = hvacRules;
        this.energy = energy;
        this.conditionPolicy = conditionPolicy;
        this.shutdownPolicy = shutdownPolicy;
    }

    /** What the room looks like now, as measured or carried over from the previous twin. */
    record Inputs(OccupancyBelief belief, KalmanStep kalmanStep, List<DeskObservation> desks, Environment environment,
                  Sensing sensing, RoomEquipment equipment, Instant now) {
    }

    TwinOutcome decide(CampusSpace space, SpaceTwinState previous, Inputs in, ReservationContext reservations,
                       TraceRecorder trace) {
        Instant now = in.now();
        int people = in.belief().people();
        Reservation active = reservations.active();
        SpaceStatus status = statusPolicy.classify(previous.maintenance(), previous.popUpActive(now), people,
                space.capacity(), active, now);
        trace.step(HexagonalLayer.DOMAIN_SERVICE, SpaceStatusPolicy.class,
                people + "/" + space.capacity() + " personas" + (active == null ? "" : ", reserva " + active.reference())
                        + " → " + status);

        ClimatePolicyFactory family = families.of(space.climateProfile());
        Environment env = in.environment();
        ThermalDecision predicted = thermal.decide(new ThermalContext(env.indoorTemperatureC(), env.outdoorTemperatureC(),
                people, space.areaSqm(), env.co2Ppm(), family.comfortBand(), minutesUntilOccupancyEnds(active, people, now),
                minutesUntilNextOccupancy(reservations, status, now)));
        trace.step(HexagonalLayer.DOMAIN_SERVICE, ThermalInertiaMpcDomainService.class,
                predicted.state() + ": " + predicted.explanation());

        boolean firstFrame = previous.lastKalmanStep() == null;
        HvacResolution resolution = hvacRules.proceed(new HvacRequest(space.hvacInstalled(), previous.maintenance(),
                previous.overrideActive(now), previous.hvac(), predicted, family,
                climateContext(people, env, in.equipment(), previous.hvac(), now), firstFrame, now));
        HvacSetting hvac = previous.hvac().moveTo(resolution.state(), resolution.mode(), resolution.setpointC(),
                resolution.explanation(), now);
        trace.step(HexagonalLayer.DOMAIN_SERVICE, HvacSettingChain.class,
                resolution.decidedBy() + " → " + hvac.state() + " / " + hvac.mode());

        double saved = firstFrame || !space.hvacInstalled() ? previous.energySavedKwh()
                : previous.energySavedKwh() + energy.savedKwh(previous.hvacState(), previous.updatedAt(), now, space.areaSqm());

        RoomEquipment equipment = in.equipment();
        Instant emptySince = emptySince(people, previous.emptySince(), now);
        RoomCondition condition = conditionPolicy.classify(previous.maintenance(), people, space.capacity(), equipment);
        EquipmentCommand command = shutdownPolicy.decide(space.spaceId(), condition, equipment, emptySince,
                previous.equipmentShutdownAt(), now).orElse(null);
        Instant shutdownAt = previous.equipmentShutdownAt();
        if (command != null) {
            // The twin assumes the order is executed; the next Arduino reading confirms or corrects it.
            equipment = equipment.shutDown();
            shutdownAt = now;
            condition = conditionPolicy.classify(previous.maintenance(), people, space.capacity(), equipment);
        }
        trace.step(HexagonalLayer.DOMAIN_SERVICE, RoomConditionPolicy.class, people + "/" + space.capacity()
                + " personas, " + IdleEquipmentShutdownPolicy.describe(equipment) + " → " + condition);

        SpaceTwinState state = previous.toBuilder()
                .status(status)
                .hvac(hvac)
                .occupancy(in.belief())
                .lastKalmanStep(in.kalmanStep())
                .lastThermalDecision(predicted)
                .environment(env)
                .sensing(in.sensing())
                .desks(in.desks())
                .energySavedKwh(round(saved))
                .equipment(equipment)
                .condition(condition)
                .timers(new RoomTimers(emptySince, shutdownAt))
                .updatedAt(now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now)
                .build();
        return new TwinOutcome(previous, state, command, trace);
    }

    static RoomEquipment equipment(CampusSpace space, boolean lightsOn, double illuminanceLux, int devicesOn) {
        return new RoomEquipment(lightsOn, illuminanceLux, Math.min(devicesOn, space.computers()), space.computers());
    }

    /** The room in the shape the climate family's strategies read, with the unit's real current setting. */
    private static ClimateContext climateContext(int people, Environment env, RoomEquipment equipment,
                                                 HvacSetting current, Instant now) {
        int minutes = (int) Math.min(current.minutesSince(now), HvacState.MAX_MINUTES);
        return new ClimateContext(Math.min(people, MeasurementLimits.MAX_PEOPLE), env.co2Ppm(), env.indoorTemperatureC(),
                env.outdoorTemperatureC(), env.humidityPct(), equipment.illuminanceLux(),
                new HvacState(current.mode(), minutes));
    }

    /** Start of the current empty period: the earliest observation of it, even if readings arrive late. */
    private static Instant emptySince(int people, Instant previous, Instant now) {
        if (people > 0) {
            return null;
        }
        return previous == null || previous.isAfter(now) ? now : previous;
    }

    private static Integer minutesUntilOccupancyEnds(Reservation active, int people, Instant now) {
        if (active == null || people == 0) {
            return null;
        }
        return (int) Math.max(0, Duration.between(now, active.endsAt()).toMinutes());
    }

    private static Integer minutesUntilNextOccupancy(ReservationContext reservations, SpaceStatus status, Instant now) {
        if (reservations.active() != null && status == SpaceStatus.RESERVADO_EN_ESPERA) {
            return 0;
        }
        Reservation next = reservations.next();
        return next == null ? null : (int) Math.max(0, Duration.between(now, next.startsAt()).toMinutes());
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
