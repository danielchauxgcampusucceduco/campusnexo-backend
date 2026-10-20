package edu.campusnexo.infrastructure.web;

import org.springframework.http.HttpStatus;
import edu.campusnexo.application.AiServiceUnavailableException;
import edu.campusnexo.application.catalog.SpaceNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> invalidRequest(IllegalArgumentException ex) {
        return Map.of("error", "invalid_request", "message", ex.getMessage());
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, Object> spaceNotFound(SpaceNotFoundException ex) {
        return Map.of("error", "space_not_found", "message", ex.getMessage());
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, Object> aiUnavailable(AiServiceUnavailableException ex) {
        return Map.of("error", "ai_service_unavailable", "message", ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> constraintViolation(ConstraintViolationException ex) {
        return Map.of("error", "validation_failed", "message", "Revisa los parámetros enviados.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> validation(MethodArgumentNotValidException ex) {
        return Map.of("error", "validation_failed", "message", "Revisa los campos enviados.");
    }
}
