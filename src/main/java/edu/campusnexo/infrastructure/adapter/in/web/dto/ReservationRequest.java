package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.CreateReservationCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code reference} is a course or group code; do not send personal data. */
public record ReservationRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull Integer expectedAttendees,
        @NotBlank @Size(max = 80) String reference) {

    public CreateReservationCommand toCommand(String spaceId) {
        return new CreateReservationCommand(spaceId, startsAt, endsAt, expectedAttendees, reference);
    }
}
