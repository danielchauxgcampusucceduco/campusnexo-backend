package edu.campusnexo.infrastructure.adapter.in.web.dto;

import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_PEOPLE;

import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.Requester;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * {@code reference} is a course or group code; do not send personal data. {@code holderCode} is optional: the
 * institutional code of the registered teacher or student responsible for the activity.
 */
public record ReservationRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Min(1) @Max(MAX_PEOPLE) Integer expectedAttendees,
        @NotBlank @Size(max = 80) String reference,
        @Size(max = 32) String holderCode) {

    /** The caller becomes the author. */
    public CreateReservationCommand toCommand(String spaceId, Requester requester) {
        return new CreateReservationCommand(spaceId, startsAt, endsAt, expectedAttendees, reference,
                holderCode == null || holderCode.isBlank() ? null : holderCode.strip(), requester);
    }
}
