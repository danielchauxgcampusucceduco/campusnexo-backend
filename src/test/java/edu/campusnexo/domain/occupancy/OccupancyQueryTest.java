package edu.campusnexo.domain.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class OccupancyQueryTest {

    @Test
    void supportsAQueryWhenTheCameraIsUnavailable() {
        OccupancyQuery query = new OccupancyQuery("AULA-301", 36, 48, null, 500, 12, 415);

        assertNull(query.cameraPeople());
        assertEquals(36, query.capacity());
    }

    @Test
    void rejectsCameraCountsAboveTheSpaceCapacityRange() {
        assertThrows(DomainValidationException.class,
                () -> new OccupancyQuery("AULA-301", 36, 48, 5_001, 500, 12, 415));
    }
}
