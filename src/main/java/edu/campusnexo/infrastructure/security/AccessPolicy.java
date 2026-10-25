package edu.campusnexo.infrastructure.security;

import edu.campusnexo.infrastructure.security.Role.Access;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What each route requires, in one table read top to bottom. Anything not listed fails closed: reads
 * under {@code /api} need a viewer, any other method needs an administrator, and every route outside
 * {@code /api} that is not listed (other actuator endpoints such as Prometheus metrics) needs an administrator.
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
            // WebSocket handshake: the STOMP CONNECT frame carries and checks the session token.
            rule("GET", "/ws(/.*)?", Access.PUBLIC),
            rule("POST", API + "/auth/login", Access.PUBLIC),
            rule("GET", API + "/auth/session", Access.PUBLIC),
            // Cameras, room boards, computers and kiosks push with their own keys.
            rule("POST", API + "/(telemetry|twin/telemetry|twin/environment|workstations/(heartbeats|audit|discovery|pairing))/?", Access.SENSOR),
            rule("POST", API + "/spaces/[^/]+/check-in/?", Access.SENSOR),
            // University administration: people, inventory and the AI tools. Reading too, since it holds people.
            rule("*", API + "/admin/.*", Access.ADMIN),
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
        // Not listed, outside /api: operational endpoints (metrics) and anything unexpected.
        return Access.ADMIN;
    }

    private static Rule rule(String method, String path, Access access) {
        return new Rule(method, Pattern.compile(path), access);
    }
}
