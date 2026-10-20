package edu.campusnexo.infrastructure.persistence.reservation;

import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaReservationAdapter implements ReservationRepository {
    private final SpringDataReservationRepository data;

    JpaReservationAdapter(SpringDataReservationRepository data) {
        this.data = data;
    }

    @Override
    @Transactional
    public Reservation save(Reservation reservation) {
        return data.save(new ReservationEntity(reservation)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findById(UUID reservationId) {
        return data.findById(reservationId).map(ReservationEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findOverlapping(String spaceId, Instant from, Instant to) {
        return data.findOverlapping(spaceId, from, to).stream().map(ReservationEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findActiveAt(String spaceId, Instant instant) {
        return data.findFirstBySpaceIdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
                spaceId, instant, instant).map(ReservationEntity::toDomain);
    }
}
