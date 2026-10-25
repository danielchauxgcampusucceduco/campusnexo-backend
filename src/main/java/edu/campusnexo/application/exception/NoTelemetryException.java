package edu.campusnexo.application.exception;

public class NoTelemetryException extends ConflictException {
    public NoTelemetryException(String spaceId) {
        super("telemetry_not_available", "El espacio " + spaceId + " no tiene lecturas de telemetría para evaluar");
    }
}
