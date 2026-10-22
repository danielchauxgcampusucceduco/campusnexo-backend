package edu.campusnexo.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OperationalSafetyCheckTest {
    @Test
    void forbidsSimulationScenariosFromRunningWithRealMqttActuators() {
        assertThrows(IllegalStateException.class, () -> new OperationalSafetyCheck(true, "MQTT", true));
        assertDoesNotThrow(() -> new OperationalSafetyCheck(true, "log", true));
        assertDoesNotThrow(() -> new OperationalSafetyCheck(false, "mqtt", true));
    }

    @Test
    void rejectsAnUnknownActuatorModeAtStartup() {
        assertThrows(IllegalStateException.class, () -> new OperationalSafetyCheck(false, "physical", false));
    }
}
