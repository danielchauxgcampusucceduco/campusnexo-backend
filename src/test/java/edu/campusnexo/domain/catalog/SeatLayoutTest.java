package edu.campusnexo.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class SeatLayoutTest {

    @Test
    void calculatesTheNumberOfSeatsInTheGrid() {
        assertEquals(36, new SeatLayout(6, 6).seats());
    }

    @Test
    void rejectsEmptyOrOversizedDimensions() {
        assertThrows(DomainValidationException.class, () -> new SeatLayout(0, 6));
        assertThrows(DomainValidationException.class, () -> new SeatLayout(6, 41));
    }
}
