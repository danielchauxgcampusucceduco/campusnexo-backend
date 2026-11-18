package edu.campusnexo.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in combinations that could let someone administer the campus who should not:
 * <ul>
 *   <li>development security (requests without credentials act as administrator) when the environment is declared
 *   production ({@code APP_ENVIRONMENT});</li>
 *   <li>production without {@code ADMIN_ALLOWED_NETWORKS}: the administration would answer from any network.</li>
 * </ul>
 */
@Component
class OperationalSafetyCheck {
    OperationalSafetyCheck(@Value("${app.security.mode}") String securityMode,
                           @Value("${app.environment}") String environment,
                           @Value("${app.security.admin-networks:}") String adminNetworks) {
        boolean development = "development".equalsIgnoreCase(securityMode.trim());
        boolean production = "production".equalsIgnoreCase(environment.trim());
        if (development && production) {
            throw new IllegalStateException("APP_ENVIRONMENT=production exige SECURITY_MODE=enforced");
        }
        if (production && adminNetworks.isBlank()) {
            throw new IllegalStateException("APP_ENVIRONMENT=production exige ADMIN_ALLOWED_NETWORKS (la IP de la VPN de "
                    + "administración): sin ella /api/v1/admin respondería desde cualquier red");
        }
    }
}
