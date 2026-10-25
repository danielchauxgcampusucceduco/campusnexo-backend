package edu.campusnexo.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.application.exception.SpaceNotFoundException;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.TestData;
import org.junit.jupiter.api.Test;

class SpaceCatalogServiceTest {
    @Test
    void filtersBySpaceTypeWithoutChangingTheCatalog() {
        SpaceCatalogService catalog = new SpaceCatalogService(new InMemorySpaceCatalogRepository(
                TestData.andeanClassroom(), TestData.library(), TestData.lab()));

        assertEquals(3, catalog.listSpaces(null).size());
        assertEquals("BIB-PISO-2", catalog.listSpaces(SpaceType.BIBLIOTECA).getFirst().spaceId());
        assertEquals(0, catalog.listSpaces(SpaceType.AUDITORIO).size());
    }

    @Test
    void mapsBlankAndUnknownIdentifiersToTheDomainNotFoundError() {
        SpaceCatalogService catalog = new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.library()));

        assertThrows(SpaceNotFoundException.class, () -> catalog.getRequiredSpace(" "));
        assertThrows(SpaceNotFoundException.class, () -> catalog.getRequiredSpace("A-999"));
        assertEquals("BIB-PISO-2", catalog.getRequiredSpace("BIB-PISO-2").spaceId());
    }
}
