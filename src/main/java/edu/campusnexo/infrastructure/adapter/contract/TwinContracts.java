package edu.campusnexo.infrastructure.adapter.contract;

import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.escenario.EscenarioOperativo;
import edu.campusnexo.domain.escenario.ReconocedorDeEscenarios;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
import edu.campusnexo.domain.twin.KalmanStep;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
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
    private static final ReconocedorDeEscenarios ESCENARIOS = ReconocedorDeEscenarios.estandar();

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

    /** {@code accesibleParaEstudiantes}: the camera follows the space and it is offered as available. */
    public record SpaceInfo(String spaceId, String name, SpaceType type, int capacity, double areaSqm,
                            String buildingId, String buildingName, String branchId, String branchName, String city,
                            int floor, int computers, Integer seatRows, Integer seatColumns,
                            boolean accesibleParaEstudiantes) {
        static SpaceInfo from(SpaceTwinView view) {
            var space = view.space();
            var layout = space.seatLayout();
            return new SpaceInfo(space.spaceId(), space.name(), space.type(), space.capacity(), space.areaSqm(),
                    space.buildingId(), space.buildingName(), space.branchId(), space.branchName(), space.city(),
                    space.floor(), space.computers(), layout == null ? null : layout.rows(),
                    layout == null ? null : layout.columns(), space.accesibleParaEstudiantes());
        }
    }

    public record ReservationInfo(UUID reservationId, Instant startsAt, Instant endsAt, int expectedAttendees,
                                  String reference, String holderCode, Instant checkedInAt) {
        static ReservationInfo from(Reservation reservation) {
            return new ReservationInfo(reservation.reservationId(), reservation.startsAt(), reservation.endsAt(),
                    reservation.expectedAttendees(), reservation.reference(), reservation.holderCode(),
                    reservation.checkedInAt());
        }
    }

    public record RoiResponse(String zoneId, String label, List<double[]> polygon) {
        static RoiResponse from(StudyZoneRoi roi) {
            return new RoiResponse(roi.zoneId(), roi.label(),
                    roi.polygon().stream().map(point -> new double[] {point.x(), point.y()}).toList());
        }
    }

    /**
     * {@code escenarios} are the operational scenarios recognized in the room at {@code updatedAt}, and
     * {@code emulated} says the camera data did not come from a real camera. {@code sensorsReporting}: the room's
     * camera has reported at least once; without it the room has no sensor, which is not the same as free.
     */
    public record TwinStateResponse(String spaceId, SpaceStatus status, int occupancy, double occupancyEstimate,
                                    double occupancyStdDev, boolean cameraOnline, Instant popUpUntil,
                                    boolean maintenance, Instant updatedAt, List<DeskResponse> desks,
                                    KalmanResponse kalman, Instant emptySince, int teachersPresent,
                                    int studentsPresent, boolean emulated, Instant lastCameraFrameAt,
                                    boolean sensorsReporting, List<EscenarioResponse> escenarios) {
        public static TwinStateResponse from(SpaceTwinState state) {
            return new TwinStateResponse(state.spaceId(), state.status(), state.occupancy().people(),
                    round(state.occupancy().estimate()), round(state.occupancy().standardDeviation()),
                    state.cameraOnline(), state.popUpUntil(), state.maintenance(), state.updatedAt(),
                    state.desks().stream().map(DeskResponse::from).toList(), KalmanResponse.from(state.lastKalmanStep()),
                    state.emptySince(), state.presence().teachers(), state.presence().students(),
                    state.sensing().emulated(), state.sensing().lastCameraFrameAt(), state.sensing().reporting(),
                    ESCENARIOS.reconocer(state, state.updatedAt()).stream().map(EscenarioResponse::from).toList());
        }
    }

    public record EscenarioResponse(EscenarioOperativo escenario, String descripcion) {
        static EscenarioResponse from(EscenarioOperativo escenario) {
            return new EscenarioResponse(escenario, escenario.descripcion());
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

    public record TraceResponse(UUID traceId, String trigger, String spaceId, Instant startedAt, List<TraceStepResponse> steps) {
        public static TraceResponse from(ArchitectureTrace trace) {
            return new TraceResponse(trace.traceId(), trace.trigger(), trace.spaceId(), trace.startedAt(),
                    trace.steps().stream().map(s -> new TraceStepResponse(s.layer(), s.component(), s.detail(),
                            s.offsetMicros())).toList());
        }
    }

    public record TraceStepResponse(HexagonalLayer layer, String component, String detail, long offsetMicros) {
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
