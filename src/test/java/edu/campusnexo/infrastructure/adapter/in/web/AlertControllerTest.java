package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.AlertQueryUseCase;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertControllerTest {
    @Test
    void exposesTheNextOpaqueCursorOnlyForAFullPage() {
        AlertQueryUseCase query = mock(AlertQueryUseCase.class);
        Alert row = Alert.raise("A-301", AlertType.CO2_ALTO, AlertSeverity.ADVERTENCIA,
                Instant.parse("2026-10-21T12:00:00Z"), "CO2 alto", 1200, 1000);
        when(query.recent("A-301", AlertType.CO2_ALTO, 1, null)).thenReturn(List.of(row));

        var page = new AlertController(query).recent("A-301", AlertType.CO2_ALTO, 1, null);

        assertEquals(1, page.getBody().size());
        assertEquals(new edu.campusnexo.domain.shared.PageCursor(row.detectedAt(), row.alertId().toString()),
                PageCursors.decode(page.getHeaders().getFirst(PageCursors.HEADER)));
        when(query.recent("A-301", AlertType.CO2_ALTO, 2, null)).thenReturn(List.of(row));
        var partial = new AlertController(query).recent("A-301", AlertType.CO2_ALTO, 2, null);
        assertNull(partial.getHeaders().getFirst(PageCursors.HEADER));
    }
}
