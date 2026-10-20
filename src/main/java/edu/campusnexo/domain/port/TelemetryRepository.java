package edu.campusnexo.domain.port;

import edu.campusnexo.domain.model.Telemetry;
import java.time.Instant;
import java.util.List;

public interface TelemetryRepository {
    Telemetry save(Telemetry telemetry);
    List<Telemetry> findRecent(String spaceId, int limit);
    long countSince(Instant from);
}
