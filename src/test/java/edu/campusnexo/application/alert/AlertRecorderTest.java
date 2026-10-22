package edu.campusnexo.application.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.support.InMemoryAlertRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertRecorderTest {
    @Test
    void suppressesAnAlertWhenTheSameTypeWasRaisedAtTheWindowBoundary() {
        Instant since = Instant.parse("2026-10-21T12:00:00Z");
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        repository.save(alert(since));
        AlertRecorder recorder = new AlertRecorder(repository);

        assertFalse(recorder.recordUnlessRaisedSince(alert(since.plusSeconds(1)), since));
        assertEquals(1, repository.rows().size());
    }

    @Test
    void recordsWhenTheExistingAlertIsOutsideTheDeduplicationWindow() {
        Instant since = Instant.parse("2026-10-21T12:00:00Z");
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        repository.save(alert(since.minusNanos(1)));
        Alert next = alert(since);

        assertTrue(new AlertRecorder(repository).recordUnlessRaisedSince(next, since));
        assertEquals(2, repository.rows().size());
        assertEquals(next, repository.rows().getLast());
    }

    private static Alert alert(Instant at) {
        return Alert.raise("A-301", AlertType.CO2_ALTO, AlertSeverity.ADVERTENCIA, at,
                "CO2 alto", 1200, 1000);
    }
}
