package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentCommandTest {

    @Test
    void carriesTheRelayActionsAndIssueTime() {
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        UUID id = UUID.fromString("593c90ad-04d2-47ee-b521-5ab421ac2001");
        EquipmentCommand command = new EquipmentCommand(id, "201-A", true, false, now, "Aula vacía");

        assertEquals(id, command.commandId());
        assertEquals("201-A", command.spaceId());
        assertEquals(now, command.issuedAt());
    }

    @Test
    void rejectsCommandsWithoutAReasonOrTarget() {
        assertThrows(DomainValidationException.class,
                () -> new EquipmentCommand(UUID.randomUUID(), " ", true, true, Instant.now(), "ahorro"));
        assertThrows(DomainValidationException.class,
                () -> new EquipmentCommand(UUID.randomUUID(), "201-A", true, true, Instant.now(), " "));
    }
}
