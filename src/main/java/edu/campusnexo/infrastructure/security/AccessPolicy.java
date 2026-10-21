package edu.campusnexo.infrastructure.security;

import edu.campusnexo.infrastructure.security.Role.Access;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What each route requires, in one table read top to bottom. Anything not listed fails closed: reads
 * under {@code /api} need a viewer and any other method needs an administrator.
 */
final class AccessPolicy {
    private record Rule(String method, Pattern path, Access access) {
        boolean matches(String requestMethod, String requestPath) {
            return (method.equals("*") || method.equals(requestMethod)) && path.matcher(requestPath).matches();
        }
    }

    private static final String API = "/api/v1";
    private static final List<Rule> RULES = List.of(
            rule("OPTIONS", ".*", Access.PUBLIC),
            rule("GET", "/actuator/health(/.*)?", Access.PUBLIC),
            rule("GET", "/actuator/info", Access.PUBLIC),
            rule("GET", "/error", Access.PUBLIC),
            rule("POST", API + "/auth/login", Access.PUBLIC),
            rule("GET", API + "/auth/session", Access.PUBLIC),
            // Cameras and room boards push readings with their own keys.
            rule("POST", API + "/(telemetry|twin/telemetry|twin/environment)/?", Access.SENSOR),
            // Demonstration scenarios run the real pipeline: administrators only.
            rule("POST", API + "/simulation/.*", Access.ADMIN),
            // Staff operations.
            rule("POST", API + "/spaces/[^/]+/(reservations|evaluations|occupancy-estimates|climate-recommendations)/?", Access.OPERATOR),
            rule("DELETE", API + "/reservations/[^/]+/?", Access.OPERATOR),
            rule("POST", API + "/twin/spaces/[^/]+/(override|popup|maintenance)/?", Access.OPERATOR),
            rule("POST", API + "/(occupancy/estimate|climate/recommendations)/?", Access.OPERATOR),
            // Everything else under the API: reading needs a viewer, changing needs an administrator.
            rule("GET", "/api/.*", Access.VIEWER),
            rule("*", "/api/.*", Access.ADMIN));

    private AccessPolicy() {
    }

    static Access required(String method, String path) {
        String upper = method.toUpperCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.matches(upper, path)) {
                return rule.access();
            }
        }
        // Outside /api: the WebSocket handshake (authenticated by STOMP CONNECT) and static resources.
        return Access.PUBLIC;
    }

    private static Rule rule(String method, String path, Access access) {
        return new Rule(method, Pattern.compile(path), access);
    }
}
