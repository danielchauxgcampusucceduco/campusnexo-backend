package edu.campusnexo.application.exception;

public class ReservationConflictException extends ConflictException {
    public ReservationConflictException(String spaceId) {
        super("reservation_conflict", "El espacio " + spaceId + " ya tiene una reserva en ese horario");
    }
}
