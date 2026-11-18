package edu.campusnexo.application.operations;

import edu.campusnexo.application.exception.NoTelemetryException;
import edu.campusnexo.application.port.in.AlertQueryUseCase;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.EstimateOccupancyCommand;
import edu.campusnexo.application.port.in.OccupancyEstimationUseCase;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.SpaceEvaluation;
import edu.campusnexo.application.port.in.SpaceEvaluationUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.util.List;

/**
 * Facade: one simple operation over the catalog, telemetry, occupancy (AI), reservation and alert subsystems. Clients
 * no longer need to know the order of the calls or copy values from one response into the next request.
 */
public class CampusOperationsFacade implements SpaceEvaluationUseCase {
    static final int RECENT_ALERTS = 5;

    private final CampusCatalogUseCase catalog;
    private final TelemetryUseCase telemetry;
    private final OccupancyEstimationUseCase occupancy;
    private final ReservationUseCase reservations;
    private final AlertQueryUseCase alerts;

    public CampusOperationsFacade(CampusCatalogUseCase catalog, TelemetryUseCase telemetry,
                                  OccupancyEstimationUseCase occupancy, ReservationUseCase reservations,
                                  AlertQueryUseCase alerts) {
        this.catalog = catalog;
        this.telemetry = telemetry;
        this.occupancy = occupancy;
        this.reservations = reservations;
        this.alerts = alerts;
    }

    @Override
    public SpaceEvaluation evaluate(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        Telemetry latest = telemetry.latest(space.spaceId()).orElseThrow(() -> new NoTelemetryException(space.spaceId()));

        // The camera's own count, not the twin's fused estimate: the estimator fuses the Wi-Fi itself, and feeding it a
        // value that already contains it would count that evidence twice.
        OccupancyRecord estimate = occupancy.estimate(new EstimateOccupancyCommand(space.spaceId(),
                latest.cameraPeople(), latest.wifiDevices()));

        Reservation activeReservation = reservations.activeAt(space.spaceId(), latest.recordedAt()).orElse(null);
        List<Alert> recentAlerts = alerts.recent(space.spaceId(), null, RECENT_ALERTS);
        return new SpaceEvaluation(space, latest, estimate, activeReservation, recentAlerts);
    }
}
