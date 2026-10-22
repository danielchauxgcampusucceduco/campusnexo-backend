package edu.campusnexo.domain.climate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class CompressorGuardTest {
    private final CompressorGuard guard = CompressorGuard.standard();

    @Test
    void holdsOnlyTheChangesThatWouldShortCycle() {
        assertTrue(guard.mustHold(true, false, 9), "apagar antes de 10 min");
        assertFalse(guard.mustHold(true, false, 10));
        assertTrue(guard.mustHold(false, true, 4), "encender antes de 5 min de reposo");
        assertFalse(guard.mustHold(false, true, 5));
        assertFalse(guard.mustHold(true, true, 0), "seguir encendido no es un cambio");
        assertFalse(guard.mustHold(false, false, 0));
    }

    @Test
    void rejectsNegativeMinimums() {
        assertThrows(DomainValidationException.class, () -> new CompressorGuard(-1, 5));
    }
}
