package edu.campusnexo.infrastructure.security;

import edu.campusnexo.infrastructure.security.Role.Access;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates every request, enforces {@link AccessPolicy}, limits writes per caller and writes an
 * audit line for every change and every refusal ({@code campusnexo.audit} logger).
 */
final class AuthenticationFilter extends OncePerRequestFilter {
    private static final Logger audit = LoggerFactory.getLogger("campusnexo.audit");

    private final Authenticator authenticator;
    private final FixedWindowRateLimiter writes;

    AuthenticationFilter(Authenticator authenticator, int writesPerMinute, Clock clock) {
        this.authenticator = authenticator;
        this.writes = new FixedWindowRateLimiter(writesPerMinute, Duration.ofMinutes(1), clock);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Access required = AccessPolicy.required(method, path);
        if (required == Access.PUBLIC) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Principal> principal = authenticator.resolve(request.getHeader(Authenticator.AUTHORIZATION_HEADER),
                request.getHeader(Authenticator.API_KEY_HEADER));
        String client = request.getRemoteAddr();
        if (principal.isEmpty()) {
            audit.warn("AUDIT denegado motivo=sin_credenciales metodo={} ruta={} ip={}", method, path, client);
            ProblemResponses.write(response, 401, "No autenticado",
                    "Inicia sesión (Authorization: Bearer) o envía la clave del sensor (X-API-Key).", "authentication_required");
            return;
        }
        Principal caller = principal.get();
        if (!caller.role().satisfies(required)) {
            audit.warn("AUDIT denegado motivo=rol usuario={} rol={} requiere={} metodo={} ruta={} ip={}", caller.name(),
                    caller.role(), required, method, path, client);
            ProblemResponses.write(response, 403, "Sin permiso",
                    "Tu rol (" + caller.role() + ") no permite esta operación; requiere " + required + ".", "forbidden");
            return;
        }
        boolean change = !method.equals("GET") && !method.equals("HEAD");
        if (change && !writes.tryAcquire(caller.name() + "@" + client)) {
            audit.warn("AUDIT denegado motivo=limite usuario={} metodo={} ruta={} ip={}", caller.name(), method, path, client);
            response.setHeader("Retry-After", "60");
            ProblemResponses.write(response, 429, "Demasiadas solicitudes",
                    "Superaste el límite de operaciones por minuto; espera un momento.", "rate_limited");
            return;
        }
        request.setAttribute(Principal.REQUEST_ATTRIBUTE, caller);
        try {
            chain.doFilter(request, response);
        } finally {
            if (change) {
                audit.info("AUDIT operacion usuario={} rol={} metodo={} ruta={} estado={} ip={}", caller.name(),
                        caller.role(), method, path, response.getStatus(), client);
            }
        }
    }

    /** ProblemDetail bodies written before the request reaches Spring MVC. */
    static final class ProblemResponses {
        private ProblemResponses() {
        }

        static void write(HttpServletResponse response, int status, String title, String detail, String code)
                throws IOException {
            response.setStatus(status);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + json(title) + "\",\"status\":" + status
                    + ",\"detail\":\"" + json(detail) + "\",\"code\":\"" + code + "\"}");
        }

        private static String json(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
