package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TwinControlServiceTest {
    private final TwinTestFixture fixture = new TwinTestFixture();

    @Test
    void acceptsControlDurationsOnlyWithinTheDocumentedTwelveHourWindow() {
        assertThrows(DomainValidationException.class,
                () -> fixture.controls.manualOverride("AULA-301", Duration.ZERO));
        assertThrows(DomainValidationException.class,
                () -> fixture.controls.openPopUpStudyRoom("AULA-301", Duration.ofHours(12).plusNanos(1)));

        var state = fixture.controls.manualOverride("AULA-301", Duration.ofHours(12));
        assertEquals(HvacControlState.OVERRIDE_MANUAL_DOCENTE, state.hvacState());
        org.junit.jupiter.api.Assertions.assertTrue(state.overrideUntil().isAfter(fixture.clock.instant()));
    }

    @Test
    void maintenanceControlSurvivesReevaluationAndResetRestoresInitialState() {
        var maintenance = fixture.controls.setMaintenance("AULA-301", true);
        assertTrue(maintenance.maintenance());

        var reset = fixture.controls.reset("AULA-301");

        assertEquals(false, reset.maintenance());
        assertEquals(HvacControlState.BLOQUEO_AHORRO_VACIO, reset.hvacState());
    }
}
