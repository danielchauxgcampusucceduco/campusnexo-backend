package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.PageCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageCursorsTest {

    @Test
    void roundTripsAnOpaqueCursorWithUnicodeAndDelimiterInTheKey() {
        PageCursor original = new PageCursor(Instant.parse("2026-10-21T15:20:00Z"), "AULA|Ñ-301");

        assertEquals(original, PageCursors.decode(PageCursors.encode(original)));
    }

    @Test
    void treatsMissingCursorAsTheFirstPage() {
        assertNull(PageCursors.decode(null));
        assertNull(PageCursors.decode("  "));
    }

    @Test
    void rejectsMalformedCursorPayloads() {
        String noSeparator = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-a-timestamp".getBytes(StandardCharsets.UTF_8));

        assertThrows(DomainValidationException.class, () -> PageCursors.decode("not base64!"));
        assertThrows(DomainValidationException.class, () -> PageCursors.decode(noSeparator));
    }

    @Test
    void returnsTheNextCursorOnlyForAFullPage() {
        PageCursor expected = new PageCursor(Instant.parse("2026-10-21T15:20:00Z"), "AULA-301");
        var fullPage = PageCursors.page(List.of("first", "last"), 2,
                ignored -> expected, String::toUpperCase);
        var shortPage = PageCursors.page(List.of("first"), 2, ignored -> expected, String::toUpperCase);

        assertEquals(PageCursors.encode(expected), fullPage.getHeaders().getFirst(PageCursors.HEADER));
        assertEquals(List.of("FIRST", "LAST"), fullPage.getBody());
        assertNull(shortPage.getHeaders().getFirst(PageCursors.HEADER));
    }
}
