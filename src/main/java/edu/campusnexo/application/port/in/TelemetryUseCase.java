package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.model.Telemetry;
import java.util.List;

public interface TelemetryUseCase {
    Telemetry ingest(Telemetry telemetry);
    List<Telemetry> recent(String spaceId, int limit);
}
