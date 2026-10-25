package edu.campusnexo.domain.reservation;

/**
 * The store refused a reservation because another one of the same space overlaps it. The store is the
 * final judge: two requests that both checked "no overlap" before saving cannot both be confirmed.
 */
public class ReservationOverlapException extends RuntimeException {
    public ReservationOverlapException(String spaceId) {
        super("El espacio " + spaceId + " ya tiene una reserva que se solapa");
    }
}
