package edu.campusnexo.domain.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class OccupancyEstimateTest {

    @Test
    void normalizesAnAbsentNoteAndKeepsTheEstimateProvenance() {
        OccupancyEstimate estimate = new OccupancyEstimate(
                "AULA-301", 12, 82.5, false, true, OccupancySource.RESPALDO_LOCAL, null);

        assertEquals("", estimate.note());
        assertEquals(OccupancySource.RESPALDO_LOCAL, estimate.source());
        assertEquals(82.5, estimate.confidencePct());
    }

    @Test
    void rejectsInvalidCountsConfidenceAndMissingSource() {
        assertThrows(DomainValidationException.class,
                () -> new OccupancyEstimate("AULA-301", -1, 50, true, true, OccupancySource.SERVICIO_IA, ""));
        assertThrows(DomainValidationException.class,
                () -> new OccupancyEstimate("AULA-301", 12, 101, true, true, OccupancySource.SERVICIO_IA, ""));
        assertThrows(DomainValidationException.class,
                () -> new OccupancyEstimate("AULA-301", 12, 50, true, true, null, ""));
    }
}
