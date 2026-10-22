package edu.campusnexo.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PageCursorTest {

    @Test
    void preservesTheTimestampAndTieBreakingKey() {
        Instant at = Instant.parse("2026-10-21T12:30:00Z");

        PageCursor cursor = new PageCursor(at, "AULA-301");

        assertEquals(at, cursor.at());
        assertEquals("AULA-301", cursor.key());
    }

    @Test
    void rejectsMissingTimestampOrKey() {
        Instant at = Instant.parse("2026-10-21T12:30:00Z");

        assertThrows(DomainValidationException.class, () -> new PageCursor(null, "AULA-301"));
        assertThrows(DomainValidationException.class, () -> new PageCursor(at, null));
    }
}
