package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.SpaceEvaluation;
import java.util.List;

public record SpaceEvaluationResponse(
        SpaceResponse space,
        TelemetryResponse telemetry,
        OccupancyEstimateResponse occupancy,
        ClimateRecommendationResponse recommendation,
        ReservationResponse activeReservation,
        List<AlertResponse> recentAlerts) {

    public static SpaceEvaluationResponse from(SpaceEvaluation evaluation) {
        return new SpaceEvaluationResponse(SpaceResponse.from(evaluation.space()),
                TelemetryResponse.from(evaluation.telemetry()),
                OccupancyEstimateResponse.from(evaluation.occupancy()),
                ClimateRecommendationResponse.from(evaluation.recommendation()),
                evaluation.activeReservation() == null ? null : ReservationResponse.from(evaluation.activeReservation()),
                evaluation.recentAlerts().stream().map(AlertResponse::from).toList());
    }
}
