package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.climate.ComfortBand;
import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class ThermalContextTest {

    @Test
    void acceptsOptionalReservationTimesAndReportsOccupancy() {
        ThermalContext empty = new ThermalContext(22, 14, 0, 48, 500,
                new ComfortBand(19, 24), null, null);
        ThermalContext occupied = new ThermalContext(22, 14, 10, 48, 500,
                new ComfortBand(19, 24), 30, 60);

        assertFalse(empty.occupied());
        assertTrue(occupied.occupied());
        assertNull(empty.minutesUntilNextOccupancy());
    }

    @Test
    void rejectsNegativeReservationTime() {
        assertThrows(DomainValidationException.class, () -> new ThermalContext(
                22, 14, 10, 48, 500, new ComfortBand(19, 24), -1, null));
    }
}
