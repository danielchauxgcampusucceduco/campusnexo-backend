package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.support.TwinTestFixture;
import org.junit.jupiter.api.Test;

class TwinQueryServiceTest {
    private final TwinTestFixture fixture = new TwinTestFixture();

    @Test
    void projectsCatalogSpacesWithoutPersistingSyntheticInitialStates() {
        var views = fixture.queries.spaces();

        assertEquals(fixture.catalog.listSpaces(null).size(), views.size());
        assertTrue(views.stream().allMatch(view -> view.state().hvacState() == HvacControlState.APAGADO));
        assertTrue(fixture.twins.findAllTwins().isEmpty());
        assertTrue(views.stream().allMatch(view -> view.rois() != null));
    }

    @Test
    void aggregatesSavingsAndReturnsTheRequestedNumberOfRecentTraces() {
        fixture.pipeline.process(
                new edu.campusnexo.domain.twin.TelemetrySnapshot("LAB-IA-102", fixture.clock.instant(), true,
                        java.util.List.of(), 430, 19.5, 13.5, 52, 400, 0, 1.0),
                "MQTT · test");

        var savings = fixture.queries.savings();

        assertEquals(fixture.catalog.listSpaces(null).size(), savings.spaces().size());
        assertTrue(savings.totalKwh() >= 0);
        assertEquals(1, fixture.queries.recentTraces(1).size());
        assertNotNull(fixture.queries.space("LAB-IA-102").state());
    }
}
