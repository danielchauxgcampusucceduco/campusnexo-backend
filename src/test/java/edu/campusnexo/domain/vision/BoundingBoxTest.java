package edu.campusnexo.domain.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class BoundingBoxTest {

    @Test
    void returnsTheNormalizedCenterPoint() {
        assertEquals(new NormalizedPoint(0.4, 0.6), new BoundingBox(0.2, 0.3, 0.6, 0.9).center());
    }

    @Test
    void rejectsBoxesOutsideTheFrameOrWithoutArea() {
        assertThrows(DomainValidationException.class, () -> new BoundingBox(-0.1, 0, 0.5, 0.5));
        assertThrows(DomainValidationException.class, () -> new BoundingBox(0.5, 0.5, 0.5, 0.8));
    }
}
