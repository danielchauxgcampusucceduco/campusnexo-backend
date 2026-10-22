package edu.campusnexo.application.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.SpaceNotFoundException;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.TestData;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertQueryServiceTest {
    @Test
    void filtersByKnownSpaceAndTypeAndHonorsTheKeysetCursor() {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        Instant firstAt = Instant.parse("2026-10-21T10:00:00Z");
        Alert first = alert(AlertType.CO2_ALTO, firstAt);
        Alert second = alert(AlertType.CO2_ALTO, firstAt.plusSeconds(60));
        repository.save(first);
        repository.save(second);
        repository.save(alert(AlertType.SOBRECUPO, firstAt.plusSeconds(120)));
        AlertQueryService service = new AlertQueryService(repository,
                new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom())));

        assertEquals(java.util.List.of(second, first), service.recent("AULA-301", AlertType.CO2_ALTO, 10, null));
        assertEquals(java.util.List.of(first), service.recent("AULA-301", AlertType.CO2_ALTO, 10,
                new PageCursor(second.detectedAt(), second.alertId().toString())));
    }

    @Test
    void validatesExplicitSpaceFiltersButTreatsBlankAsAllSpaces() {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        AlertQueryService service = new AlertQueryService(repository,
                new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom())));

        assertThrows(SpaceNotFoundException.class, () -> service.recent("UNKNOWN", null, 10, null));
        assertEquals(java.util.List.of(), service.recent("  ", null, 10, null));
    }

    private static Alert alert(AlertType type, Instant at) {
        return Alert.raise("AULA-301", type, AlertSeverity.ADVERTENCIA, at, "alert", 1200, 1000);
    }
}
