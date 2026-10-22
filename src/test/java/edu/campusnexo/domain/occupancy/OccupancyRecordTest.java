package edu.campusnexo.domain.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OccupancyRecordTest {

    @Test
    void groupsAnEstimateWithItsAuditIdentityAndTime() {
        Instant at = Instant.parse("2026-10-21T15:00:00Z");
        OccupancyEstimate estimate = new OccupancyEstimate(
                "AULA-301", 8, 70, false, true, OccupancySource.RESPALDO_LOCAL, "estimación local");
        UUID id = UUID.fromString("cb64f249-7d18-42aa-b5bc-34777e702301");
        OccupancyRecord record = new OccupancyRecord(id, at, estimate);

        assertEquals(id, record.estimateId());
        assertEquals(at, record.estimatedAt());
        assertEquals(estimate, record.estimate());
    }

    @Test
    void rejectsMissingAuditFields() {
        assertThrows(DomainValidationException.class,
                () -> new OccupancyRecord(null, Instant.now(), new OccupancyEstimate(
                        "AULA-301", 1, 50, true, true, OccupancySource.SERVICIO_IA, "")));
    }
}
