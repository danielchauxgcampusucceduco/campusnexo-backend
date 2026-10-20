package edu.campusnexo.infrastructure.web.dto;

import edu.campusnexo.application.port.in.DashboardSummary;
import edu.campusnexo.domain.alert.AlertType;
import java.time.Instant;
import java.util.Map;

/** {@code monitoredSpaces}, {@code readingsLast24h} and {@code since} keep their original names. */
public record DashboardSummaryResponse(
        long monitoredSpaces,
        long readingsLast24h,
        Instant since,
        long alertsLast24h,
        Map<AlertType, Long> alertsByType,
        Double averageOccupancyPct,
        int saturatedSpaces,
        double estimatedEmptySpaceEnergyKwh,
        double estimatedEmptySpaceEnergyCostCop) {

    public static DashboardSummaryResponse from(DashboardSummary summary) {
        return new DashboardSummaryResponse(summary.monitoredSpaces(), summary.readingsLast24h(), summary.since(),
                summary.alertsLast24h(), summary.alertsByType(), summary.averageOccupancyPct(),
                summary.saturatedSpaces(), summary.estimatedEmptySpaceEnergyKwh(),
                summary.estimatedEmptySpaceEnergyCostCop());
    }
}
