package edu.campusnexo.infrastructure.web;

import edu.campusnexo.application.AiServiceUnavailableException;
import edu.campusnexo.application.catalog.SpaceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SpaceNotFoundException.class)
    ProblemDetail spaceNotFound(SpaceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Espacio no encontrado", exception.getMessage(), "space_not_found");
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    ProblemDetail aiUnavailable(AiServiceUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de IA no disponible",
                exception.getMessage(), "ai_service_unavailable");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud no válida", exception.getMessage(), "invalid_request");
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    ProblemDetail validation(Exception exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Error de validación",
                "Revisa los parámetros y campos enviados.", "validation_failed");
        if (exception instanceof MethodArgumentNotValidException invalidBody) {
            List<ValidationError> errors = invalidBody.getBindingResult().getFieldErrors().stream()
                    .map(error -> new ValidationError(error.getField(),
                            error.getDefaultMessage() == null ? "Valor no válido" : error.getDefaultMessage()))
                    .distinct()
                    .sorted(Comparator.comparing(ValidationError::field))
                    .toList();
            detail.setProperty("errors", errors);
        } else if (exception instanceof ConstraintViolationException invalidParameter) {
            List<ValidationError> errors = invalidParameter.getConstraintViolations().stream()
                    .map(error -> new ValidationError(error.getPropertyPath().toString(), error.getMessage()))
                    .distinct()
                    .sorted(Comparator.comparing(ValidationError::field))
                    .toList();
            detail.setProperty("errors", errors);
        }
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail malformedRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud mal formada",
                "Revisa el formato JSON, los tipos de datos y los parámetros requeridos.", "malformed_request");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }

    private record ValidationError(String field, String message) {}
}
