package edu.campusnexo.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResultLimitTest {

    @Test
    void acceptsInclusiveLowerAndUpperBounds() {
        assertEquals(1, ResultLimit.of(1).value());
        assertEquals(ResultLimit.MAX, ResultLimit.of(ResultLimit.MAX).value());
    }

    @Test
    void rejectsValuesOutsideTheSupportedRange() {
        assertThrows(DomainValidationException.class, () -> ResultLimit.of(0));
        assertThrows(DomainValidationException.class, () -> ResultLimit.of(ResultLimit.MAX + 1));
    }
}
