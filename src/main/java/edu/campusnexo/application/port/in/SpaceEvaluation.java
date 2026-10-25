package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.util.List;

/** Result of the end-to-end evaluation; {@code activeReservation} may be {@code null}. */
public record SpaceEvaluation(
        CampusSpace space,
        Telemetry telemetry,
        OccupancyRecord occupancy,
        ClimateRecommendation recommendation,
        Reservation activeReservation,
        List<Alert> recentAlerts) {
}
