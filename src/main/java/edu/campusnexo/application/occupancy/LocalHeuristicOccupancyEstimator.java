package edu.campusnexo.application.occupancy;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.domain.twin.KalmanParameters;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService.FusedOccupancy;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import java.util.Locale;

/**
 * Local estimate used when the AI service is down or its circuit is open. It is the same fusion model as the
 * digital twin and the AI service (inverse-variance fusion of camera, CO₂ mass balance and Wi-Fi with the
 * same noise parameters), so the answer does not change model when the service fails, only where it is
 * computed. Parameters are illustrative until measured against campus counts.
 */
public class LocalHeuristicOccupancyEstimator implements OccupancyEstimator {
    private final OccupancyKalmanDomainService fusion;

    public LocalHeuristicOccupancyEstimator(KalmanParameters parameters) {
        this.fusion = new OccupancyKalmanDomainService(parameters);
    }

    public LocalHeuristicOccupancyEstimator() {
        this(KalmanParameters.defaults());
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        FusedOccupancy fused = fusion.fuse(query.cameraPeople(), query.co2Ppm(), query.wifiDevices(), query.areaSqm(),
                query.outdoorCo2Ppm());
        int people = (int) Math.min(query.capacity() * 3L, Math.round(fused.estimate()));
        return new OccupancyEstimate(query.spaceId(), people, fused.confidencePct(query.capacity()),
                query.cameraPeople() != null, true, OccupancySource.RESPALDO_LOCAL, String.format(Locale.ROOT,
                "Respaldo local: fusión por varianza inversa de %d fuentes, σ = %.1f personas (parámetros no calibrados)",
                fused.measurements().size(), fused.standardDeviation()));
    }
}
