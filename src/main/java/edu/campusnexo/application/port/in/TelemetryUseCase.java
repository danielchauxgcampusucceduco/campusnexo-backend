package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.telemetry.Telemetry;
import java.util.List;
import java.util.Optional;

public interface TelemetryUseCase {
    /** Idempotent: the same space and instant are stored once. */
    IngestResult ingest(RecordTelemetryCommand command);

    List<Telemetry> recent(String spaceId, int limit);

    Optional<Telemetry> latest(String spaceId);

    record IngestResult(Telemetry telemetry, boolean created) {
    }
}
