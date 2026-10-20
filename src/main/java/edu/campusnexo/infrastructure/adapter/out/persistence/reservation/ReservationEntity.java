package edu.campusnexo.infrastructure.adapter.out.persistence.reservation;

import edu.campusnexo.domain.reservation.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
class ReservationEntity {
    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "space_id", nullable = false, length = 32)
    private String spaceId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "expected_attendees", nullable = false)
    private int expectedAttendees;

    @Column(name = "reference", nullable = false, length = 80)
    private String reference;

    protected ReservationEntity() {
    }

    ReservationEntity(Reservation reservation) {
        reservationId = reservation.reservationId();
        spaceId = reservation.spaceId();
        startsAt = reservation.startsAt();
        endsAt = reservation.endsAt();
        expectedAttendees = reservation.expectedAttendees();
        reference = reservation.reference();
    }

    Reservation toDomain() {
        return new Reservation(reservationId, spaceId, startsAt, endsAt, expectedAttendees, reference);
    }
}
