package edu.campusnexo.infrastructure.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects the write endpoints used by sensors and staff (telemetry ingestion, edge frames, reservations
 * and manual twin controls) with a shared API key in the {@code X-API-Key} header. Read endpoints, the
 * dashboard's estimate/evaluation calls and the demonstration scenarios stay open. This is a prototype control, not user
 * authentication: it does not identify people or roles.
 */
@Component
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    static final String HEADER = "X-API-Key";
    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final Pattern PROTECTED_POST = Pattern.compile(
            "^/api/v1/(telemetry|twin/telemetry|spaces/[^/]+/reservations|twin/spaces/[^/]+/(override|popup|maintenance))/?$");
    private static final Pattern PROTECTED_DELETE = Pattern.compile("^/api/v1/reservations/[^/]+/?$");

    private final byte[] expectedKey;

    ApiKeyAuthenticationFilter(@Value("${app.security.ingest-api-key}") String apiKey,
                               @Value("${app.security.require-ingest-api-key}") boolean required) {
        if (apiKey == null || apiKey.isBlank()) {
            if (required) {
                throw new IllegalStateException("INGEST_API_KEY es obligatorio con el perfil de producción");
            }
            log.warn("INGEST_API_KEY no está definido: la ingesta de telemetría y las reservas quedan sin protección");
            this.expectedKey = null;
        } else {
            this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (expectedKey == null) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (request.getMethod().toUpperCase(Locale.ROOT)) {
            case "POST" -> !PROTECTED_POST.matcher(path).matches();
            case "DELETE" -> !PROTECTED_DELETE.matcher(path).matches();
            default -> true;
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"No autorizado\",\"status\":401,"
                + "\"detail\":\"Falta la cabecera " + HEADER + " o no es válida.\",\"code\":\"api_key_required\"}");
    }
}
