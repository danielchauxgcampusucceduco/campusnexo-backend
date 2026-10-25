package edu.campusnexo.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in combinations that could let someone act on the building who should not:
 * <ul>
 *   <li>development security (requests without credentials act as administrator) with real actuators;</li>
 *   <li>development security when the environment is declared production ({@code APP_ENVIRONMENT});</li>
 *   <li>production without {@code ADMIN_ALLOWED_NETWORKS}: the administration would answer from any network.</li>
 * </ul>
 * With {@code app.actuators.mode=log} (the default) the orders only reach the log and the dashboard.
 */
@Component
class OperationalSafetyCheck {
    private static final Logger log = LoggerFactory.getLogger(OperationalSafetyCheck.class);

    OperationalSafetyCheck(@Value("${app.actuators.mode}") String actuatorsMode,
                           @Value("${app.mqtt.enabled}") boolean mqttEnabled,
                           @Value("${app.security.mode}") String securityMode,
                           @Value("${app.environment}") String environment,
                           @Value("${app.security.admin-networks:}") String adminNetworks) {
        boolean realActuators = mqttEnabled && "mqtt".equalsIgnoreCase(actuatorsMode.trim());
        if (!"mqtt".equalsIgnoreCase(actuatorsMode.trim()) && !"log".equalsIgnoreCase(actuatorsMode.trim())) {
            throw new IllegalStateException("app.actuators.mode debe ser 'log' o 'mqtt': " + actuatorsMode);
        }
        boolean development = "development".equalsIgnoreCase(securityMode.trim());
        if (development && realActuators) {
            throw new IllegalStateException("La seguridad en modo desarrollo (sin credenciales = administrador) no "
                    + "puede convivir con actuadores reales: usa SECURITY_MODE=enforced o ACTUATORS_MODE=log");
        }
        boolean production = "production".equalsIgnoreCase(environment.trim());
        if (development && production) {
            throw new IllegalStateException("APP_ENVIRONMENT=production exige SECURITY_MODE=enforced");
        }
        if (production && adminNetworks.isBlank()) {
            throw new IllegalStateException("APP_ENVIRONMENT=production exige ADMIN_ALLOWED_NETWORKS (la IP de la VPN de "
                    + "administración): sin ella /api/v1/admin respondería desde cualquier red");
        }
        log.info("Actuadores: {}", realActuators ? "MQTT (ESP32 y Arduino reales)" : "solo registro en el log");
    }
}
