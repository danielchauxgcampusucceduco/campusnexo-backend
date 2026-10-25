package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.util.List;
import java.util.Optional;

public interface TelemetryUseCase {
    /** Idempotent: the same space and instant are stored once. */
    IngestResult ingest(RecordTelemetryCommand command);

    /** Newest first; pass the cursor of the last row of a page to get the next one. */
    List<Telemetry> recent(String spaceId, int limit, PageCursor before);

    default List<Telemetry> recent(String spaceId, int limit) {
        return recent(spaceId, limit, null);
    }

    /** Cursor that continues after {@code last}. */
    static PageCursor cursorAfter(Telemetry last) {
        return new PageCursor(last.recordedAt(), last.spaceId());
    }

    Optional<Telemetry> latest(String spaceId);

    record IngestResult(Telemetry telemetry, boolean created) {
    }
}
