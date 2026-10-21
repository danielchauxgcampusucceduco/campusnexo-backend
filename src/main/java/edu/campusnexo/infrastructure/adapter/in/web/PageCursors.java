package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.PageCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;

/**
 * Opaque cursors for keyset pagination. Lists stay JSON arrays (compatible with existing clients); when a
 * page is full, {@value #HEADER} carries the cursor of the next one ({@code ?cursor=...}).
 */
final class PageCursors {
    static final String HEADER = "X-Next-Cursor";

    private PageCursors() {
    }

    static PageCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new PageCursor(Instant.parse(raw.substring(0, separator)), raw.substring(separator + 1));
        } catch (IllegalArgumentException | DateTimeParseException | StringIndexOutOfBoundsException malformed) {
            throw new DomainValidationException("cursor no válido");
        }
    }

    static String encode(PageCursor cursor) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((cursor.at() + "|" + cursor.key()).getBytes(StandardCharsets.UTF_8));
    }

    /** The page as a JSON array, with the next cursor when the page is full. */
    static <T, R> ResponseEntity<List<R>> page(List<T> rows, int limit, Function<T, PageCursor> cursorOf,
                                               Function<T, R> view) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (!rows.isEmpty() && rows.size() == limit) {
            response.header(HEADER, encode(cursorOf.apply(rows.get(rows.size() - 1))));
        }
        return response.body(rows.stream().map(view).toList());
    }
}
