package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class ComfortBandTest {

    @Test
    void selectsTheReferenceBandForEachClimateProfile() {
        assertEquals(new ComfortBand(19, 24), ComfortBand.forProfile(ClimateProfile.SEDE_ANDINA));
        assertEquals(new ComfortBand(23, 26), ComfortBand.forProfile(ClimateProfile.SEDE_TROPICAL));
        assertEquals(new ComfortBand(20, 25), ComfortBand.forProfile(ClimateProfile.SEDE_TEMPLADA));
    }

    @Test
    void includesBothComfortBoundariesAndRejectsAnInvertedBand() {
        ComfortBand band = new ComfortBand(19, 24);
        assertTrue(band.contains(19));
        assertTrue(band.contains(24));
        assertFalse(band.contains(24.1));
        assertEquals(21.5, band.midpoint());
        assertThrows(DomainValidationException.class, () -> new ComfortBand(24, 19));
    }
}
