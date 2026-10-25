package edu.campusnexo.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OperationalSafetyCheckTest {
    @Test
    void allowsRealMqttActuatorsWithEnforcedSecurity() {
        assertDoesNotThrow(() -> check("MQTT", true, "enforced", "local"));
        assertDoesNotThrow(() -> check("log", true, "enforced", "local"));
    }

    @Test
    void rejectsAnUnknownActuatorModeAtStartup() {
        assertThrows(IllegalStateException.class, () -> check("physical", false, "enforced", "local"));
    }

    @Test
    void forbidsDevelopmentSecurityWithRealActuators() {
        assertThrows(IllegalStateException.class, () -> check("mqtt", true, "development", "local"));
        assertDoesNotThrow(() -> check("log", false, "development", "local"));
    }

    @Test
    void forbidsDevelopmentSecurityInProduction() {
        assertThrows(IllegalStateException.class, () -> check("log", false, "development", "production"));
        assertDoesNotThrow(() -> check("log", false, "enforced", "production"));
    }

    @Test
    void productionNeedsTheAdministrationNetwork() {
        assertThrows(IllegalStateException.class,
                () -> new OperationalSafetyCheck("log", false, "enforced", "production", ""));
        assertThrows(IllegalStateException.class,
                () -> new OperationalSafetyCheck("log", false, "enforced", "production", "  "));
        assertDoesNotThrow(() -> new OperationalSafetyCheck("log", false, "enforced", "local", ""),
                "en local, sin red configurada, la administración solo exige el rol");
    }

    private static OperationalSafetyCheck check(String actuators, boolean mqtt, String security, String environment) {
        return new OperationalSafetyCheck(actuators, mqtt, security, environment, "203.0.113.7/32");
    }
}
