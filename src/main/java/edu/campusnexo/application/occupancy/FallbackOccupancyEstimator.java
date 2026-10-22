package edu.campusnexo.application.occupancy;

import edu.campusnexo.application.port.out.OperationalMetrics;
import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimationFailedException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.port.OccupancyEstimator;

/**
 * Decorator: when the wrapped estimator fails, answers with a secondary estimator so the campus keeps
 * working while the AI service is down. The result is marked with its real source and every fallback is
 * counted, so a silent outage shows up on the operations dashboard.
 */
public class FallbackOccupancyEstimator implements OccupancyEstimator {
    private final OccupancyEstimator primary;
    private final OccupancyEstimator fallback;
    private final OperationalMetrics metrics;

    public FallbackOccupancyEstimator(OccupancyEstimator primary, OccupancyEstimator fallback, OperationalMetrics metrics) {
        this.primary = primary;
        this.fallback = fallback;
        this.metrics = metrics;
    }

    public FallbackOccupancyEstimator(OccupancyEstimator primary, OccupancyEstimator fallback) {
        this(primary, fallback, OperationalMetrics.none());
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        try {
            return primary.estimate(query);
        } catch (OccupancyEstimationFailedException failure) {
            metrics.aiFallback(failure instanceof OccupancyEstimatorUnavailableException ? "no_disponible" : "respuesta_invalida");
            return fallback.estimate(query);
        }
    }
}
