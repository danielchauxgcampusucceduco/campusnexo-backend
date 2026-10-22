package edu.campusnexo.domain.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import org.junit.jupiter.api.Test;

class TelemetryRecordedTest {

    @Test
    void couplesThePersistedReadingWithItsCatalogSpace() {
        var reading = TestData.reading("AULA-301", TestData.NOW, 8, 600, 1.5);
        var space = TestData.andeanClassroom();

        assertEquals(reading, new TelemetryRecorded(reading, space).telemetry());
        assertEquals(space, new TelemetryRecorded(reading, space).space());
    }

    @Test
    void rejectsEventsWithoutTheReadingOrSpace() {
        assertThrows(DomainValidationException.class, () -> new TelemetryRecorded(null, TestData.andeanClassroom()));
        assertThrows(DomainValidationException.class,
                () -> new TelemetryRecorded(TestData.reading("AULA-301", TestData.NOW, 8, 600, 1.5), null));
    }
}
