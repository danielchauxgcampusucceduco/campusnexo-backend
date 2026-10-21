package edu.campusnexo.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.exception.SpaceNotFoundException;
import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import edu.campusnexo.application.port.in.TelemetryUseCase.IngestResult;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.InMemoryTelemetryRepository;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryServiceTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final InMemoryTelemetryRepository repository = new InMemoryTelemetryRepository();
    private final List<TelemetryRecorded> notified = new ArrayList<>();
    private final TelemetryService service = new TelemetryService(clock, repository,
            new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom())),
            new TelemetryEventPublisher(List.of(notified::add), (observer, event, failure) -> { }),
            Duration.ofMinutes(5), Duration.ofDays(30));

    private static RecordTelemetryCommand command(String spaceId, Instant at) {
        return new RecordTelemetryCommand(spaceId, at, 18, 0, 21, 820, 23.1, 58, 410, 2.4);
    }

    @Test
    @DisplayName("Sin recordedAt usa el reloj del servidor, guarda y notifica a los observadores")
    void storesAndNotifies() {
        IngestResult result = service.ingest(command("AULA-301", null));
        assertTrue(result.created());
        assertEquals(TestData.NOW, result.telemetry().recordedAt());
        assertEquals(1, repository.rows().size());
        assertEquals(1, notified.size());
    }

    @Test
    @DisplayName("Reenviar la misma lectura es idempotente y no vuelve a notificar")
    void duplicateReadingIsIdempotent() {
        Instant at = TestData.NOW.minusSeconds(60);
        service.ingest(command("AULA-301", at));
        IngestResult second = service.ingest(command("AULA-301", at));
        assertFalse(second.created());
        assertEquals(1, repository.rows().size());
        assertEquals(1, notified.size());
    }

    @Test
    @DisplayName("Rechaza lecturas del futuro o demasiado antiguas")
    void rejectsInvalidTimestamps() {
        assertThrows(DomainValidationException.class,
                () -> service.ingest(command("AULA-301", TestData.NOW.plus(Duration.ofMinutes(10)))));
        assertThrows(DomainValidationException.class,
                () -> service.ingest(command("AULA-301", TestData.NOW.minus(Duration.ofDays(31)))));
    }

    @Test
    @DisplayName("Rechaza espacios que no existen y límites fuera de rango")
    void rejectsUnknownSpacesAndLimits() {
        assertThrows(SpaceNotFoundException.class, () -> service.ingest(command("AULA-999", null)));
        assertThrows(DomainValidationException.class, () -> service.recent(null, 0));
        assertThrows(DomainValidationException.class, () -> service.recent(null, 101));
    }

    @Test
    @DisplayName("La paginación por cursor no salta ni repite lecturas aunque varias compartan el mismo instante")
    void keysetPagination() {
        for (int minute = 0; minute < 3; minute++) {
            for (String space : java.util.List.of("AULA-301", "BIB-PISO-2")) {
                repository.save(TestData.reading(space, TestData.NOW.minus(java.time.Duration.ofMinutes(minute)), 5, 600, 1));
            }
        }
        java.util.List<edu.campusnexo.domain.telemetry.Telemetry> first = service.recent(null, 4);
        java.util.List<edu.campusnexo.domain.telemetry.Telemetry> second = service.recent(null, 4,
                edu.campusnexo.application.port.in.TelemetryUseCase.cursorAfter(first.get(first.size() - 1)));
        assertEquals(4, first.size());
        assertEquals(2, second.size());
        java.util.Set<String> seen = new java.util.HashSet<>();
        first.forEach(row -> seen.add(row.spaceId() + row.recordedAt()));
        second.forEach(row -> seen.add(row.spaceId() + row.recordedAt()));
        assertEquals(6, seen.size());
    }
}
