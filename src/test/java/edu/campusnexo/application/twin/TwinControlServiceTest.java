package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TwinControlServiceTest {
    private final TwinTestFixture fixture = new TwinTestFixture();

    @Test
    void acceptsControlDurationsOnlyWithinTheDocumentedTwelveHourWindow() {
        assertThrows(DomainValidationException.class,
                () -> fixture.controls.openPopUpStudyRoom("AULA-301", Duration.ZERO));
        assertThrows(DomainValidationException.class,
                () -> fixture.controls.openPopUpStudyRoom("AULA-301", Duration.ofHours(12).plusNanos(1)));

        var state = fixture.controls.openPopUpStudyRoom("AULA-301", Duration.ofHours(12));
        assertEquals(SpaceStatus.HABILITADO_POPUP_PARCIALES, state.status());
        assertTrue(state.popUpUntil().isAfter(fixture.clock.instant()));
    }

    @Test
    void maintenanceControlSurvivesReevaluationAndResetRestoresInitialState() {
        var maintenance = fixture.controls.setMaintenance("AULA-301", true);
        assertTrue(maintenance.maintenance());
        assertEquals(SpaceStatus.MANTENIMIENTO, maintenance.status());

        var reset = fixture.controls.reset("AULA-301");

        assertEquals(false, reset.maintenance());
        assertEquals(SpaceStatus.LIBRE, reset.status());
    }
}
