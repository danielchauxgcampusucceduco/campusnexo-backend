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
 * not contain personal data. {@code origin} says whether a person or the demonstration created it, and
 * {@code createdBy} is the account that did ({@code null} for bookings made before authorship was recorded);
 * both are typed fields, never inferred from the reference text.
 */
public record Reservation(
        UUID reservationId,
        String spaceId,
        Instant startsAt,
        Instant endsAt,
        int expectedAttendees,
        String reference,
        ReservationOrigin origin,
        String createdBy) {

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
        requireNonNull(origin, "origin");
        if (createdBy != null) {
            requireText(createdBy, "createdBy", 64);
        }
    }

    /** A booking made by a person, author unknown (legacy rows and tests). */
    public Reservation(UUID reservationId, String spaceId, Instant startsAt, Instant endsAt, int expectedAttendees,
                       String reference) {
        this(reservationId, spaceId, startsAt, endsAt, expectedAttendees, reference, ReservationOrigin.USUARIO, null);
    }

    public boolean createdBySimulation() {
        return origin == ReservationOrigin.SIMULACION;
    }

    public boolean isActiveAt(Instant instant) {
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
