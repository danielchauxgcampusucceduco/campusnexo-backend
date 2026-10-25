package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.reservation.Reservation;

/** A confirmed check-in: the booking with its arrival time and the person who confirmed it. */
public record CheckIn(Reservation reservation, Persona persona) {
}
