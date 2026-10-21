package edu.campusnexo.domain.shared;

import java.time.Instant;

/**
 * Keyset position in a list ordered from newest to oldest: the next page holds the rows strictly older
 * than ({@code at}, {@code key}). The key breaks ties between rows of the same instant (the space for
 * telemetry, the alert id for alerts), so no row is skipped or repeated across pages, however many rows
 * are inserted meanwhile.
 */
public record PageCursor(Instant at, String key) {
    public PageCursor {
        DomainChecks.requireNonNull(at, "cursor.at");
        DomainChecks.requireNonNull(key, "cursor.key");
    }
}
