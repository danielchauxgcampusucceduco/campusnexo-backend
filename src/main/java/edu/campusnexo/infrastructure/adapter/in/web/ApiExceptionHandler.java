package edu.campusnexo.infrastructure.web;

import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorInvalidResponseException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.shared.DomainValidationException;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is a {@link ProblemDetail} with a stable {@code code}. Standard Spring MVC errors
 * (404 route, 405, 415, malformed JSON...) are handled by the base class and receive a code here;
 * unexpected exceptions become a 500 without leaking internal messages.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail invalidRequest(DomainValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud no válida", exception.getMessage(), "invalid_request");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage(), exception.code());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflicto con el estado actual", exception.getMessage(), exception.code());
    }

    @ExceptionHandler(OccupancyEstimatorUnavailableException.class)
    ProblemDetail aiUnavailable(OccupancyEstimatorUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de IA no disponible", exception.getMessage(),
                "ai_service_unavailable");
    }

    @ExceptionHandler(OccupancyEstimatorInvalidResponseException.class)
    ProblemDetail aiInvalidResponse(OccupancyEstimatorInvalidResponseException exception) {
        log.warn("Respuesta no válida del servicio de IA: {}", exception.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Respuesta no válida del servicio de IA", exception.getMessage(),
                "ai_service_invalid_response");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Error de validación",
                "Revisa los parámetros enviados.", "validation_failed");
        detail.setProperty("errors", exception.getConstraintViolations().stream()
                .map(violation -> new ValidationError(violation.getPropertyPath().toString(), violation.getMessage()))
                .distinct().sorted(Comparator.comparing(ValidationError::field)).toList());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado; revisa los registros del servidor.", "internal_error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Error de validación",
                "Revisa los campos enviados.", "validation_failed");
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(),
                        error.getDefaultMessage() == null ? "Valor no válido" : error.getDefaultMessage()))
                .distinct().sorted(Comparator.comparing(ValidationError::field)).toList();
        detail.setProperty("errors", errors);
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail detail && !hasCode(detail)) {
            boolean validation = exception instanceof HandlerMethodValidationException;
            detail.setProperty("code", validation ? "validation_failed" : codeFor(statusCode));
            if (statusCode.value() == 400) {
                detail.setTitle(validation ? "Error de validación" : "Solicitud mal formada");
                detail.setDetail("Revisa el formato JSON, los tipos de datos y los parámetros requeridos.");
            }
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail detail) {
        Map<String, Object> properties = detail.getProperties();
        return properties != null && properties.containsKey("code");
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "malformed_request";
            case 404 -> "route_not_found";
            case 405 -> "method_not_allowed";
            case 406 -> "not_acceptable";
            case 415 -> "unsupported_media_type";
            default -> "http_" + status.value();
        };
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }

    private record ValidationError(String field, String message) {
    }
}
