package edu.campusnexo.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in combinations that could let a demonstration act on the building:
 * the scenarios run the real decision pipeline, so they must never be wired to real actuators.
 * With {@code app.actuators.mode=log} (the default) the orders only reach the log and the dashboard.
 */
@Component
class OperationalSafetyCheck {
    private static final Logger log = LoggerFactory.getLogger(OperationalSafetyCheck.class);

    OperationalSafetyCheck(@Value("${app.simulation.enabled}") boolean simulationEnabled,
                           @Value("${app.actuators.mode}") String actuatorsMode,
                           @Value("${app.mqtt.enabled}") boolean mqttEnabled) {
        boolean realActuators = mqttEnabled && "mqtt".equalsIgnoreCase(actuatorsMode.trim());
        if (!"mqtt".equalsIgnoreCase(actuatorsMode.trim()) && !"log".equalsIgnoreCase(actuatorsMode.trim())) {
            throw new IllegalStateException("app.actuators.mode debe ser 'log' o 'mqtt': " + actuatorsMode);
        }
        if (simulationEnabled && realActuators) {
            throw new IllegalStateException("Los escenarios de simulación no pueden convivir con actuadores reales: "
                    + "usa SIMULATION_ENABLED=false o ACTUATORS_MODE=log");
        }
        if (simulationEnabled) {
            log.warn("Escenarios de simulación habilitados (solo administradores); las órdenes van al log, no a equipos");
        }
        log.info("Actuadores: {}", realActuators ? "MQTT (ESP32 y Arduino reales)" : "solo registro en el log");
    }
}
