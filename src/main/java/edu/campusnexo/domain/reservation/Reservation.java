package edu.campusnexo.domain.reservation;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.telemetry.MeasurementLimits;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Booking of a space. {@code reference} identifies the activity (course or group code); it must
 * not contain personal data.
 */
public record Reservation(
        UUID reservationId,
        String spaceId,
        Instant startsAt,
        Instant endsAt,
        int expectedAttendees,
        String reference) {

    public static final Duration MAX_DURATION = Duration.ofHours(12);

    public Reservation {
        requireNonNull(reservationId, "reservationId");
        requireText(spaceId, "spaceId");
        requireNonNull(startsAt, "startsAt");
        requireNonNull(endsAt, "endsAt");
        require(endsAt.isAfter(startsAt), "endsAt debe ser posterior a startsAt");
        require(!Duration.between(startsAt, endsAt).minus(MAX_DURATION).isPositive(),
                "Una reserva no puede durar más de " + MAX_DURATION.toHours() + " horas");
        requireRange(expectedAttendees, 1, MeasurementLimits.MAX_PEOPLE, "expectedAttendees");
        requireText(reference, "reference", 80);
    }

    public boolean isActiveAt(Instant instant) {
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
