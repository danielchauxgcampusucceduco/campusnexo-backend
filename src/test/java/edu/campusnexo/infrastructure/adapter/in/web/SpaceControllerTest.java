package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpaceControllerTest {
    @Test
    void mapsCatalogDomainSpacesToThePublicResponseContract() {
        CampusCatalogUseCase catalog = mock(CampusCatalogUseCase.class);
        when(catalog.listSpaces(SpaceType.AULA)).thenReturn(List.of(TestData.andeanClassroom()));
        SpaceController controller = new SpaceController(catalog);

        var response = controller.spaces(SpaceType.AULA);

        assertEquals(1, response.size());
        assertEquals("AULA-301", response.getFirst().spaceId());
        assertEquals(32, response.getFirst().capacity());
        assertEquals("Pasto", response.getFirst().city());
        verify(catalog).listSpaces(SpaceType.AULA);
    }

    @Test
    void delegatesRequiredSpaceLookupByIdentifier() {
        CampusCatalogUseCase catalog = mock(CampusCatalogUseCase.class);
        when(catalog.getRequiredSpace("AULA-301")).thenReturn(TestData.andeanClassroom());

        assertEquals("AULA-301", new SpaceController(catalog).space("AULA-301").spaceId());
        verify(catalog).getRequiredSpace("AULA-301");
    }
}
