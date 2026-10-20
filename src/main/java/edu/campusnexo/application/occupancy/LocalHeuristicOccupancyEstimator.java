package edu.campusnexo.application.occupancy;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.OccupancyEstimator;

/**
 * Local estimate used when the AI service is down. Illustrative, uncalibrated heuristic: assumes
 * roughly one occupant per {@code 1000 / area} ppm of CO2 above outdoor level and 0.8 people per
 * Wi-Fi device. Confidence is reported low on purpose.
 */
public class LocalHeuristicOccupancyEstimator implements OccupancyEstimator {
    static final double PEOPLE_PER_DEVICE = 0.8;
    static final double CAMERA_CONFIDENCE_PCT = 60;
    static final double SIGNAL_CONFIDENCE_PCT = 35;

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        int maxPlausible = query.capacity() * 2;
        if (query.cameraPeople() != null) {
            int people = Math.min(query.cameraPeople(), maxPlausible);
            return new OccupancyEstimate(query.spaceId(), people, CAMERA_CONFIDENCE_PCT, true, true,
                    OccupancySource.RESPALDO_LOCAL, "Respaldo local: se usa el conteo de cámara sin fusión de señales");
        }
        double co2Excess = Math.max(0, query.co2Ppm() - query.outdoorCo2Ppm());
        double fromCo2 = co2Excess * query.areaSqm() / 1000.0;
        double fromWifi = query.wifiDevices() * PEOPLE_PER_DEVICE;
        int people = (int) Math.min(maxPlausible, Math.round((fromCo2 + fromWifi) / 2));
        return new OccupancyEstimate(query.spaceId(), people, SIGNAL_CONFIDENCE_PCT, false, true,
                OccupancySource.RESPALDO_LOCAL, "Respaldo local con CO2 y Wi-Fi (heurística ilustrativa, no calibrada)");
    }
}
