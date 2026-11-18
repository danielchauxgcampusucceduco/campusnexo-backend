package edu.campusnexo.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import org.junit.jupiter.api.Test;

class CampusSpaceTest {

    @Test
    void supportsSpacesWithAndWithoutSeatGrids() {
        CampusSpace classroom = TestData.room("201-A", SpaceType.AULA, 36, 6, 6, 1);
        CampusSpace office = TestData.andeanClassroom();

        assertEquals(36, classroom.seatLayout().seats());
        assertNull(office.seatLayout());
        assertEquals(0, office.computers());
    }

    @Test
    void rejectsZeroCapacityAndMissingType() {
        assertThrows(DomainValidationException.class,
                () -> new CampusSpace("201-A", "Aula", SpaceType.AULA, 0, 48,
                        "BLOQUE-A", "Bloque A", "UCC", "Pasto", "Pasto"));
        assertThrows(DomainValidationException.class,
                () -> new CampusSpace("201-A", "Aula", null, 36, 48,
                        "BLOQUE-A", "Bloque A", "UCC", "Pasto", "Pasto"));
    }

    @Test
    void onlyAcademicAndStudySpacesAreForStudents() {
        assertTrue(TestData.andeanClassroom().accesibleParaEstudiantes());
        assertTrue(TestData.library().accesibleParaEstudiantes());
        assertTrue(TestData.lab().accesibleParaEstudiantes());
        assertFalse(TestData.office().accesibleParaEstudiantes());
        assertFalse(SpaceType.LOGISTICA.accesibleParaEstudiantes());
    }
}
