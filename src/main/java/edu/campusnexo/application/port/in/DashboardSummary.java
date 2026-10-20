package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.alert.AlertType;
import java.time.Instant;
import java.util.Map;

/**
 * Operational indicators of the last 24 hours. Energy values are estimates computed from the
 * demo readings; they are not a metered energy audit.
 */
public record DashboardSummary(
        Instant since,
        long monitoredSpaces,
        long readingsLast24h,
        long alertsLast24h,
        Map<AlertType, Long> alertsByType,
        Double averageOccupancyPct,
        int saturatedSpaces,
        double estimatedEmptySpaceEnergyKwh,
        double estimatedEmptySpaceEnergyCostCop) {
}
