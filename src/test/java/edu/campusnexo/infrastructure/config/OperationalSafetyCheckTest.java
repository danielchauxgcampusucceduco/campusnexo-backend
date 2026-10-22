package edu.campusnexo.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OperationalSafetyCheckTest {
    @Test
    void forbidsSimulationScenariosFromRunningWithRealMqttActuators() {
        assertThrows(IllegalStateException.class, () -> check(true, "MQTT", true, "enforced", "local"));
        assertDoesNotThrow(() -> check(true, "log", true, "enforced", "local"));
        assertDoesNotThrow(() -> check(false, "mqtt", true, "enforced", "local"));
    }

    @Test
    void rejectsAnUnknownActuatorModeAtStartup() {
        assertThrows(IllegalStateException.class, () -> check(false, "physical", false, "enforced", "local"));
    }

    @Test
    void forbidsDevelopmentSecurityWithRealActuators() {
        assertThrows(IllegalStateException.class, () -> check(false, "mqtt", true, "development", "local"));
        assertDoesNotThrow(() -> check(true, "log", false, "development", "local"));
    }

    @Test
    void forbidsDevelopmentSecurityInProduction() {
        assertThrows(IllegalStateException.class, () -> check(false, "log", false, "development", "production"));
        assertDoesNotThrow(() -> check(false, "log", false, "enforced", "production"));
    }

    private static OperationalSafetyCheck check(boolean simulation, String actuators, boolean mqtt, String security,
                                                String environment) {
        return new OperationalSafetyCheck(simulation, actuators, mqtt, security, environment);
    }
}
