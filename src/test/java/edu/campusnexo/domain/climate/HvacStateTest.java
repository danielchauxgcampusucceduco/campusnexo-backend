package edu.campusnexo.domain.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class HvacStateTest {

    @Test
    void initialStateIsOffForOneDay() {
        assertEquals(new HvacState(HvacMode.APAGADO, 1_440), HvacState.initial());
    }

    @Test
    void acceptsTheMaximumDurationAndRejectsInvalidState() {
        assertEquals(HvacState.MAX_MINUTES, new HvacState(HvacMode.CALEFACCION, HvacState.MAX_MINUTES).minutesInMode());
        assertThrows(DomainValidationException.class, () -> new HvacState(null, 0));
        assertThrows(DomainValidationException.class, () -> new HvacState(HvacMode.APAGADO, -1));
    }
}
