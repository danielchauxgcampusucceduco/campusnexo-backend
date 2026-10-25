package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.CheckIn;
import edu.campusnexo.domain.persona.TipoPersona;
import java.time.Instant;
import java.util.UUID;

/** What the kiosk shows after a check-in: whose booking it is and when the arrival was recorded. */
public record CheckInResponse(UUID reservationId, String spaceId, String reference, TipoPersona tipo, String nombre,
                              Instant checkedInAt) {

    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(checkIn.reservation().reservationId(), checkIn.reservation().spaceId(),
                checkIn.reservation().reference(), checkIn.persona().tipo(), checkIn.persona().nombre(),
                checkIn.reservation().checkedInAt());
    }
}
