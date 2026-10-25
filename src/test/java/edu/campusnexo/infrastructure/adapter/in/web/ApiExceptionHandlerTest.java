package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import edu.campusnexo.application.exception.SpaceNotFoundException;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsDomainValidationAndMissingResourcesToStableProblemCodes() {
        var invalid = handler.invalidRequest(new DomainValidationException("medición no válida"));
        var missing = handler.notFound(new SpaceNotFoundException("A-999"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), invalid.getStatus());
        assertEquals("invalid_request", invalid.getProperties().get("code"));
        assertEquals("medición no válida", invalid.getDetail());
        assertEquals(HttpStatus.NOT_FOUND.value(), missing.getStatus());
        assertEquals("space_not_found", missing.getProperties().get("code"));
    }

    @Test
    void mapsAiOutageSeparatelyAndRedactsUnexpectedMessages() {
        var unavailable = handler.aiUnavailable(new OccupancyEstimatorUnavailableException(new RuntimeException("timeout")));
        var internal = handler.unexpected(new IllegalStateException("database-password"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), unavailable.getStatus());
        assertEquals("ai_service_unavailable", unavailable.getProperties().get("code"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), internal.getStatus());
        assertEquals("internal_error", internal.getProperties().get("code"));
        assertFalse(internal.getDetail().contains("database-password"));
    }
}
