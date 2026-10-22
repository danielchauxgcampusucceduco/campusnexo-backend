package edu.campusnexo.domain.climate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class ClimateContextTest {

    @Test
    void exposesWhetherTheSpaceHasPeople() {
        assertTrue(context(1).occupied());
        assertFalse(context(0).occupied());
    }

    @Test
    void rejectsOutOfRangeOccupancyAndMissingCurrentHvac() {
        assertThrows(DomainValidationException.class, () -> context(-1));
        assertThrows(DomainValidationException.class,
                () -> new ClimateContext(0, 400, 20, 12, 50, 100, null));
    }

    private ClimateContext context(int occupancy) {
        return new ClimateContext(occupancy, 500, 22, 14, 55, 300, HvacState.initial());
    }
}
