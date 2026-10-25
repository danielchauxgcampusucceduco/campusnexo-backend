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
 * Booking of a space. {@code reference} identifies the activity (course or group code); it must not contain
 * personal data. {@code createdBy} is the account that booked it ({@code null} for tools and tests).
 * {@code holderCode} is the institutional code of the person responsible for the activity, usually the teacher,
 * and {@code checkedInAt} records when that person confirmed attendance at the room's kiosk.
 */
public record Reservation(
        UUID reservationId,
        String spaceId,
        Instant startsAt,
        Instant endsAt,
        int expectedAttendees,
        String reference,
        String createdBy,
        String holderCode,
        Instant checkedInAt) {

    public static final Duration MAX_DURATION = Duration.ofHours(12);
    /** The holder may check in this long before the booking starts. */
    public static final Duration CHECK_IN_EARLY = Duration.ofMinutes(15);

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
        if (createdBy != null) {
            requireText(createdBy, "createdBy", 64);
        }
        if (holderCode != null) {
            requireText(holderCode, "holderCode", 32);
        }
        require(checkedInAt == null || holderCode != null, "Solo una reserva con titular registra check-in");
    }

    /** A booking without author or holder (tools and tests). */
    public Reservation(UUID reservationId, String spaceId, Instant startsAt, Instant endsAt, int expectedAttendees,
                       String reference) {
        this(reservationId, spaceId, startsAt, endsAt, expectedAttendees, reference, null, null, null);
    }

    public boolean isActiveAt(Instant instant) {
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }

    public boolean checkedIn() {
        return checkedInAt != null;
    }

    /**
     * The holder confirms attendance with their institutional code. Repeating it keeps the first time, so a
     * kiosk that retries never moves the arrival later.
     */
    public Reservation checkIn(String code, Instant now) {
        requireNonNull(now, "now");
        require(holderCode != null, "La reserva no tiene titular");
        require(holderCode.equals(code), "El código no corresponde al titular de la reserva");
        require(!now.isBefore(startsAt.minus(CHECK_IN_EARLY)) && now.isBefore(endsAt),
                "El check-in solo se acepta desde " + CHECK_IN_EARLY.toMinutes() + " minutos antes del inicio y hasta el final");
        if (checkedIn()) {
            return this;
        }
        return new Reservation(reservationId, spaceId, startsAt, endsAt, expectedAttendees, reference, createdBy,
                holderCode, now);
    }
}
