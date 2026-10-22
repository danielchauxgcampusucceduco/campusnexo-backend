package edu.campusnexo.domain.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.policy.ClimateFamilies;
import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class ComfortBandTest {

    @Test
    void eachClimateFamilyDefinesItsOwnBand() {
        ClimateFamilies families = ClimateFamilies.standard();
        assertEquals(new ComfortBand(19, 24), families.of(ClimateProfile.SEDE_ANDINA).comfortBand());
        assertEquals(new ComfortBand(23, 26), families.of(ClimateProfile.SEDE_TROPICAL).comfortBand());
        assertEquals(new ComfortBand(20, 25), families.of(ClimateProfile.SEDE_TEMPLADA).comfortBand());
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
