package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HvacCommandTest {

    @Test
    void derivesCompressorStateAndReasonFromTheThermalDecision() {
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        ThermalDecision decision = new ThermalDecision(
                HvacControlState.CONFORT_ACTIVO, 22.5, 30, 5, List.of(24.0, 23.0), "Mantener confort");

        HvacCommand command = HvacCommand.of("201-A", decision, decision.state(), now);

        assertEquals("201-A", command.spaceId());
        assertTrue(command.compressorOn());
        assertEquals(22.5, command.setpointC());
        assertEquals("Mantener confort", command.reason());
        assertEquals(now, command.issuedAt());
        assertFalse(command.commandId().toString().isBlank());
    }
}
