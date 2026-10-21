package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.shared.PageCursor;
import java.util.List;

public interface AlertQueryUseCase {
    /** Newest first; pass the cursor of the last row of a page to get the next one. */
    List<Alert> recent(String spaceId, AlertType type, int limit, PageCursor before);

    default List<Alert> recent(String spaceId, AlertType type, int limit) {
        return recent(spaceId, type, limit, null);
    }

    /** Cursor that continues after {@code last}. */
    static PageCursor cursorAfter(Alert last) {
        return new PageCursor(last.detectedAt(), last.alertId().toString());
    }
}
