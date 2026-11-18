package edu.campusnexo.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OperationalSafetyCheckTest {
    @Test
    void forbidsDevelopmentSecurityInProduction() {
        assertThrows(IllegalStateException.class, () -> check("development", "production"));
        assertDoesNotThrow(() -> check("enforced", "production"));
        assertDoesNotThrow(() -> check("development", "local"));
    }

    @Test
    void productionNeedsTheAdministrationNetwork() {
        assertThrows(IllegalStateException.class,
                () -> new OperationalSafetyCheck("enforced", "production", ""));
        assertThrows(IllegalStateException.class,
                () -> new OperationalSafetyCheck("enforced", "production", "  "));
        assertDoesNotThrow(() -> new OperationalSafetyCheck("enforced", "local", ""),
                "en local, sin red configurada, la administración solo exige el rol");
    }

    private static OperationalSafetyCheck check(String security, String environment) {
        return new OperationalSafetyCheck(security, environment, "203.0.113.7/32");
    }
}
