package edu.campusnexo.infrastructure.adapter.contract;

import edu.campusnexo.application.port.in.SavingsReport;
import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.reservation.ReservationOrigin;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.ThermalDecision;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.ZoneDeskState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON contracts of the digital twin, shared by the REST controller and the STOMP notifier so that the
 * dashboard receives exactly the same shape from both channels.
 */
public final class TwinContracts {
    private TwinContracts() {
    }

    public record TwinSpaceResponse(SpaceInfo space, TwinStateResponse state, ReservationInfo activeReservation,
                                    List<RoiResponse> rois) {
        public static TwinSpaceResponse from(SpaceTwinView view) {
            return new TwinSpaceResponse(SpaceInfo.from(view), TwinStateResponse.from(view.state()),
                    view.activeReservation() == null ? null : ReservationInfo.from(view.activeReservation()),
                    view.rois().stream().map(RoiResponse::from).toList());
        }
    }

    public record SpaceInfo(String spaceId, String name, SpaceType type, int capacity, double areaSqm,
                            String buildingId, String buildingName, String branchId, String branchName, String city,
                            ClimateProfile climateProfile, double energyTariffCopPerKwh, int floor, int computers,
                            Integer seatRows, Integer seatColumns, boolean hvacInstalled, boolean simulationAllowed) {
        static SpaceInfo from(SpaceTwinView view) {
            var space = view.space();
            var layout = space.seatLayout();
            return new SpaceInfo(space.spaceId(), space.name(), space.type(), space.capacity(), space.areaSqm(),
                    space.buildingId(), space.buildingName(), space.branchId(), space.branchName(), space.city(),
                    space.climateProfile(), space.energyTariffCopPerKwh(), space.floor(), space.computers(),
                    layout == null ? null : layout.rows(), layout == null ? null : layout.columns(), space.hvacInstalled(),
                    space.simulationAllowed());
        }
    }

    public record ReservationInfo(UUID reservationId, Instant startsAt, Instant endsAt, int expectedAttendees,
                                  String reference, ReservationOrigin origin) {
        static ReservationInfo from(Reservation reservation) {
            return new ReservationInfo(reservation.reservationId(), reservation.startsAt(), reservation.endsAt(),
                    reservation.expectedAttendees(), reservation.reference(), reservation.origin());
        }
    }

    public record RoiResponse(String zoneId, String label, List<double[]> polygon) {
        static RoiResponse from(StudyZoneRoi roi) {
            return new RoiResponse(roi.zoneId(), roi.label(),
                    roi.polygon().stream().map(point -> new double[] {point.x(), point.y()}).toList());
        }
    }

    /**
     * {@code hvacState} is the strategy, {@code hvacMode} what the unit does in it and {@code hvacReason} the
     * explanation of the rule that decided it; {@code thermal} is the predictive controller's own proposal.
     */
    public record TwinStateResponse(String spaceId, SpaceStatus status, HvacControlState hvacState,
                                    Instant hvacStateSince, HvacMode hvacMode, double hvacSetpointC, String hvacReason,
                                    int occupancy, double occupancyEstimate,
                                    double occupancyStdDev, double indoorTemperatureC, double outdoorTemperatureC,
                                    double co2Ppm, double humidityPct, boolean cameraOnline, double energySavedKwh,
                                    Instant overrideUntil, Instant popUpUntil, boolean maintenance, Instant updatedAt,
                                    List<DeskResponse> desks, KalmanResponse kalman, ThermalResponse thermal,
                                    RoomCondition condition, boolean lightsOn, double illuminanceLux, int devicesOn,
                                    int devicesTotal, Instant emptySince, Instant equipmentShutdownAt) {
        public static TwinStateResponse from(SpaceTwinState state) {
            return new TwinStateResponse(state.spaceId(), state.status(), state.hvacState(), state.hvacStateSince(),
                    state.hvacMode(), state.hvac().setpointC(), state.hvac().reason(), state.occupancy().people(), round(state.occupancy().estimate()),
                    round(state.occupancy().standardDeviation()), state.indoorTemperatureC(), state.outdoorTemperatureC(),
                    state.co2Ppm(), state.humidityPct(), state.cameraOnline(), state.energySavedKwh(),
                    state.overrideUntil(), state.popUpUntil(), state.maintenance(), state.updatedAt(),
                    state.desks().stream().map(DeskResponse::from).toList(),
                    KalmanResponse.from(state.lastKalmanStep()), ThermalResponse.from(state.lastThermalDecision()),
                    state.condition(), state.equipment().lightsOn(), state.equipment().illuminanceLux(),
                    state.equipment().devicesOn(), state.equipment().devicesTotal(), state.emptySince(),
                    state.equipmentShutdownAt());
        }
    }

    public record DeskResponse(String zoneId, ZoneDeskState state, int peopleInside, int belongingsInside,
                               Instant unattendedSince, long unattendedMinutes, Instant evaluatedAt) {
        static DeskResponse from(DeskObservation desk) {
            return new DeskResponse(desk.zoneId(), desk.state(), desk.peopleInside(), desk.belongingsInside(),
                    desk.unattendedSince(), desk.unattendedMinutes(), desk.evaluatedAt());
        }
    }

    public record KalmanResponse(double priorEstimate, double priorVariance, double elapsedMinutes,
                                 double predictedVariance, List<MeasurementResponse> measurements,
                                 double posteriorEstimate, double posteriorVariance) {
        static KalmanResponse from(KalmanStep step) {
            if (step == null) {
                return null;
            }
            return new KalmanResponse(round(step.previous().estimate()), round(step.previous().variance()),
                    round(step.elapsedMinutes()), round(step.predictedVariance()),
                    step.measurements().stream().map(m -> new MeasurementResponse(m.source(), round(m.value()),
                            round(m.variance()), round(m.gain()))).toList(),
                    round(step.posterior().estimate()), round(step.posterior().variance()));
        }
    }

    public record MeasurementResponse(MeasurementSource source, double value, double variance, double gain) {
    }

    public record ThermalResponse(HvacControlState recommendedState, HvacMode mode, double setpointC, int horizonMinutes,
                                  int stepMinutes, List<Double> freeRunningTrajectoryC, String explanation) {
        static ThermalResponse from(ThermalDecision decision) {
            if (decision == null) {
                return null;
            }
            return new ThermalResponse(decision.state(), decision.mode(), decision.setpointC(), decision.horizonMinutes(),
                    decision.stepMinutes(), decision.freeRunningTrajectoryC(), decision.explanation());
        }
    }

    public record SavingsResponse(Instant generatedAt, double totalKwh, double totalCop, double totalKgCo2,
                                  double emissionFactorKgCo2PerKwh, List<SavingsReport.SpaceSavings> spaces) {
        public static SavingsResponse from(SavingsReport report) {
            return new SavingsResponse(report.generatedAt(), report.totalKwh(), report.totalCop(), report.totalKgCo2(),
                    report.emissionFactorKgCo2PerKwh(), report.spaces());
        }
    }

    public record TraceResponse(UUID traceId, String trigger, String spaceId, Instant startedAt, List<TraceStepResponse> steps) {
        public static TraceResponse from(ArchitectureTrace trace) {
            return new TraceResponse(trace.traceId(), trace.trigger(), trace.spaceId(), trace.startedAt(),
                    trace.steps().stream().map(s -> new TraceStepResponse(s.layer(), s.component(), s.detail(),
                            s.offsetMicros())).toList());
        }
    }

    public record TraceStepResponse(HexagonalLayer layer, String component, String detail, long offsetMicros) {
    }

    public record ScenarioResponse(SimulationScenario scenario, String spaceId, String description) {
        public static ScenarioResponse from(SimulationScenario scenario) {
            return new ScenarioResponse(scenario, scenario.spaceId(), scenario.description());
        }
    }

    public record SimulationResponse(SimulationScenario scenario, List<TwinSpaceResponse> spaces, TraceResponse trace) {
        public static SimulationResponse from(SimulationResult result) {
            return new SimulationResponse(result.scenario(), result.spaces().stream().map(TwinSpaceResponse::from).toList(),
                    result.trace() == null ? null : TraceResponse.from(result.trace()));
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
