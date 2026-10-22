package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.climate.HvacMode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HvacCommandTest {

    @Test
    void carriesTheStrategyTheModeAndTheReasonOfTheAppliedSetting() {
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        HvacSetting setting = new HvacSetting(HvacControlState.PRE_CALENTAMIENTO_IA, HvacMode.CALEFACCION, 19.5, now,
                "Clase en 20 min");

        HvacCommand command = HvacCommand.of("201-A", setting, "Clase en 20 min", now);

        assertEquals("201-A", command.spaceId());
        assertEquals(HvacMode.CALEFACCION, command.mode());
        assertTrue(command.compressorOn());
        assertEquals(19.5, command.setpointC());
        assertEquals("Clase en 20 min", command.reason());
        assertEquals(now, command.issuedAt());
        assertFalse(command.commandId().toString().isBlank());
    }
}
