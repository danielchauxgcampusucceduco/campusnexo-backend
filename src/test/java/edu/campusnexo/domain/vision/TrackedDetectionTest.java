package edu.campusnexo.domain.vision;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class TrackedDetectionTest {

    @Test
    void distinguishesPeopleFromBelongings() {
        assertFalse(new TrackedDetection(1, DetectedObjectClass.PERSON, 0.9, box()).objectClass().isBelonging());
        assertTrue(new TrackedDetection(2, DetectedObjectClass.BACKPACK, 0.8, box()).objectClass().isBelonging());
    }

    @Test
    void validatesTrackIdConfidenceAndRequiredValues() {
        assertThrows(DomainValidationException.class,
                () -> new TrackedDetection(-1, DetectedObjectClass.PERSON, 0.9, box()));
        assertThrows(DomainValidationException.class,
                () -> new TrackedDetection(1, DetectedObjectClass.PERSON, 1.1, box()));
        assertThrows(DomainValidationException.class,
                () -> new TrackedDetection(1, null, 0.5, box()));
    }

    private BoundingBox box() {
        return new BoundingBox(0.2, 0.2, 0.4, 0.4);
    }
}
