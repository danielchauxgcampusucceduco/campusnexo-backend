package edu.campusnexo.application.twin;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.ComfortBand;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.IdleEquipmentShutdownPolicy;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.OccupancyBelief;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.RoomConditionPolicy;
import edu.campusnexo.domain.twin.RoomEquipment;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceStatusPolicy;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.ThermalContext;
import edu.campusnexo.domain.twin.ThermalDecision;
import edu.campusnexo.domain.twin.ThermalInertiaMpcDomainService;
import edu.campusnexo.domain.vision.DeskObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Applies the domain policies to one room, in order: reservation and capacity status, thermal strategy,
 * energy saved, overall room condition and the idle shutdown order. It only reads reservations and has
 * no side effects, so {@link TwinWriter} can recompute it after a concurrent write.
 */
public final class TwinDecisionEngine {
    private final ReservationRepository reservations;
    private final SpaceStatusPolicy statusPolicy;
    private final ThermalInertiaMpcDomainService thermal;
    private final EnergyAccountingPolicy energy;
    private final RoomConditionPolicy conditionPolicy;
    private final IdleEquipmentShutdownPolicy shutdownPolicy;

    public TwinDecisionEngine(ReservationRepository reservations, SpaceStatusPolicy statusPolicy,
                              ThermalInertiaMpcDomainService thermal, EnergyAccountingPolicy energy,
                              RoomConditionPolicy conditionPolicy, IdleEquipmentShutdownPolicy shutdownPolicy) {
        this.reservations = reservations;
        this.statusPolicy = statusPolicy;
        this.thermal = thermal;
        this.energy = energy;
        this.conditionPolicy = conditionPolicy;
        this.shutdownPolicy = shutdownPolicy;
    }

    /** What the room looks like now, as measured or carried over from the previous twin. */
    record Inputs(OccupancyBelief belief, KalmanStep kalmanStep, List<DeskObservation> desks, double indoorC,
                  double outdoorC, double co2Ppm, double humidityPct, boolean cameraOnline, RoomEquipment equipment,
                  Instant now) {
    }

    TwinOutcome decide(CampusSpace space, SpaceTwinState previous, Inputs in, TraceRecorder trace) {
        Instant now = in.now();
        int people = in.belief().people();
        Reservation active = reservations.findActiveAt(space.spaceId(), now).orElse(null);
        SpaceStatus status = statusPolicy.classify(previous.maintenance(), previous.popUpActive(now), people,
                space.capacity(), active, now);
        trace.step(HexagonalLayer.DOMAIN_SERVICE, "SpaceStatusPolicy",
                people + "/" + space.capacity() + " personas" + (active == null ? "" : ", reserva " + active.reference())
                        + " → " + status);

        ThermalDecision decision = thermal.decide(new ThermalContext(in.indoorC(), in.outdoorC(), people,
                space.areaSqm(), in.co2Ppm(), ComfortBand.forProfile(space.climateProfile()),
                minutesUntilOccupancyEnds(active, people, now),
                minutesUntilNextOccupancy(space.spaceId(), active, status, now)));
        HvacControlState hvacState = previous.maintenance() ? HvacControlState.APAGADO
                : previous.overrideActive(now) ? HvacControlState.OVERRIDE_MANUAL_DOCENTE
                : decision.state();
        trace.step(HexagonalLayer.DOMAIN_SERVICE, "ThermalInertiaMpcDomainService", hvacState + ": " + decision.explanation());

        boolean firstFrame = previous.lastKalmanStep() == null;
        double saved = firstFrame ? previous.energySavedKwh()
                : previous.energySavedKwh() + energy.savedKwh(previous.hvacState(), previous.updatedAt(), now, space.areaSqm());
        Instant hvacSince = hvacState == previous.hvacState() && !firstFrame ? previous.hvacStateSince() : now;

        RoomEquipment equipment = in.equipment();
        Instant emptySince = people > 0 ? null : previous.emptySince() != null ? previous.emptySince() : now;
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
        trace.step(HexagonalLayer.DOMAIN_SERVICE, "RoomConditionPolicy", people + "/" + space.capacity()
                + " personas, " + IdleEquipmentShutdownPolicy.describe(equipment) + " → " + condition);

        SpaceTwinState state = new SpaceTwinState(space.spaceId(), status, hvacState, hvacSince, in.belief(),
                in.kalmanStep(), decision, in.indoorC(), in.outdoorC(), in.co2Ppm(), in.humidityPct(), in.cameraOnline(),
                in.desks(), round(saved), previous.overrideUntil(), previous.popUpUntil(), previous.maintenance(),
                now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now, equipment, condition, emptySince,
                shutdownAt);
        return new TwinOutcome(previous, state, command, trace);
    }

    static RoomEquipment equipment(CampusSpace space, boolean lightsOn, double illuminanceLux, int devicesOn) {
        return new RoomEquipment(lightsOn, illuminanceLux, Math.min(devicesOn, space.computers()), space.computers());
    }

    private static Integer minutesUntilOccupancyEnds(Reservation active, int people, Instant now) {
        if (active == null || people == 0) {
            return null;
        }
        return (int) Math.max(0, Duration.between(now, active.endsAt()).toMinutes());
    }

    private Integer minutesUntilNextOccupancy(String spaceId, Reservation active, SpaceStatus status, Instant now) {
        if (active != null && status == SpaceStatus.RESERVADO_EN_ESPERA) {
            return 0;
        }
        Instant after = active != null ? active.endsAt() : now;
        return reservations.findNextStartingAfter(spaceId, after)
                .map(next -> (int) Math.max(0, Duration.between(now, next.startsAt()).toMinutes()))
                .orElse(null);
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
