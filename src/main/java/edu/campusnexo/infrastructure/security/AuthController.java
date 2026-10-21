package edu.campusnexo.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in for people and the session the dashboard asks for before calling the API. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    private static final Logger audit = LoggerFactory.getLogger("campusnexo.audit");

    private final Authenticator authenticator;
    private final FixedWindowRateLimiter attempts;

    AuthController(Authenticator authenticator, SecurityProperties properties, Clock clock) {
        this.authenticator = authenticator;
        this.attempts = new FixedWindowRateLimiter(properties.loginAttemptsPerMinute(), Duration.ofMinutes(1), clock);
    }

    record LoginRequest(@NotBlank @Size(max = 40) String username, @NotBlank @Size(max = 200) String password) {
    }

    record LoginResponse(String token, String tokenType, String username, Role role, Instant expiresAt) {
    }

    record SessionResponse(String mode, boolean authenticated, String username, Role role, Instant expiresAt) {
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String client = http.getRemoteAddr();
        if (!attempts.tryAcquire(client)) {
            audit.warn("AUDIT login motivo=limite usuario={} ip={}", request.username(), client);
            return problem(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos",
                    "Espera un minuto antes de volver a intentar.", "rate_limited");
        }
        Optional<Authenticator.Session> session = authenticator.login(request.username(), request.password().toCharArray());
        if (session.isEmpty()) {
            audit.warn("AUDIT login fallido usuario={} ip={}", request.username(), client);
            return problem(HttpStatus.UNAUTHORIZED, "Credenciales no válidas", "Usuario o contraseña incorrectos.",
                    "invalid_credentials");
        }
        Principal principal = session.get().principal();
        audit.info("AUDIT login correcto usuario={} rol={} ip={}", principal.name(), principal.role(), client);
        return ResponseEntity.ok(new LoginResponse(session.get().token(), "Bearer", principal.name(), principal.role(),
                principal.expiresAt()));
    }

    /** Public: tells the dashboard whether it must sign in and who the current token belongs to. */
    @GetMapping("/session")
    SessionResponse session(HttpServletRequest http) {
        String mode = authenticator.development() ? "development" : "enforced";
        Optional<Principal> principal = authenticator.resolve(http.getHeader(Authenticator.AUTHORIZATION_HEADER), null);
        return principal.map(p -> new SessionResponse(mode, true, p.name(), p.role(), p.expiresAt()))
                .orElseGet(() -> new SessionResponse(mode, false, null, null, null));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
