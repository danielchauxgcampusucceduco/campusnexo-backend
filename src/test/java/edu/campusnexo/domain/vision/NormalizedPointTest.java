package edu.campusnexo.domain.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class NormalizedPointTest {

    @Test
    void acceptsInclusiveImageBoundaries() {
        assertEquals(new NormalizedPoint(0, 1), new NormalizedPoint(0, 1));
    }

    @Test
    void rejectsCoordinatesOutsideTheNormalizedFrame() {
        assertThrows(DomainValidationException.class, () -> new NormalizedPoint(-0.01, 0.5));
        assertThrows(DomainValidationException.class, () -> new NormalizedPoint(0.5, 1.01));
    }
}
