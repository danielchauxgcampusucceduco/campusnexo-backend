package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.support.TwinTestFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TwinQueryServiceTest {
    private final TwinTestFixture fixture = new TwinTestFixture();

    @Test
    void projectsCatalogSpacesWithoutPersistingSyntheticInitialStates() {
        var views = fixture.queries.spaces();

        assertEquals(fixture.catalog.listSpaces(null).size(), views.size());
        assertTrue(views.stream().allMatch(view -> view.state().status() == SpaceStatus.LIBRE
                && !view.state().sensing().reporting()));
        assertTrue(fixture.twins.findAllTwins().isEmpty());
        assertTrue(views.stream().allMatch(view -> view.rois() != null));
    }

    @Test
    void returnsTheRequestedNumberOfRecentTraces() {
        fixture.pipeline.process(new TelemetrySnapshot("LAB-IA-102", fixture.clock.instant(), true, List.of()),
                "MQTT · test");

        assertEquals(1, fixture.queries.recentTraces(1).size());
        assertNotNull(fixture.queries.space("LAB-IA-102").state());
        assertTrue(fixture.queries.space("LAB-IA-102").state().sensing().reporting());
    }

    @Test
    @DisplayName("La vista del campus hace una consulta de regiones para todos los salones, no una por salón")
    void campusViewReadsInBatch() {
        int before = fixture.twins.roiQueries;
        var views = fixture.queries.spaces();
        assertTrue(views.size() > 10);
        assertEquals(before + 1, fixture.twins.roiQueries);
    }
}
