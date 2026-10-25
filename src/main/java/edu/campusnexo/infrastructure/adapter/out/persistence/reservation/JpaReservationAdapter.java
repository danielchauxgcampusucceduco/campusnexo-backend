package edu.campusnexo.infrastructure.adapter.out.persistence.reservation;

import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.reservation.ReservationOverlapException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            return data.saveAndFlush(new ReservationEntity(reservation)).toDomain();
        } catch (DataIntegrityViolationException violation) {
            if (isExclusionViolation(violation)) {
                throw new ReservationOverlapException(reservation.spaceId());
            }
            throw violation;
        }
    }

    /** SQLSTATE 23P01: the {@code reservations_no_overlap} exclusion constraint. */
    private static boolean isExclusionViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, Reservation> findActiveAtForAll(Instant instant) {
        Map<String, Reservation> active = new LinkedHashMap<>();
        for (ReservationEntity entity : data.findActiveAt(instant)) {
            Reservation reservation = entity.toDomain();
            active.putIfAbsent(reservation.spaceId(), reservation);
        }
        return active;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findNextStartingAfter(String spaceId, Instant instant) {
        return data.findFirstBySpaceIdAndStartsAtGreaterThanOrderByStartsAtAsc(spaceId, instant)
                .map(ReservationEntity::toDomain);
    }

    @Override
    @Transactional
    public void delete(UUID reservationId) {
        data.deleteById(reservationId);
    }

    @Override
    @Transactional
    public void recordCheckIn(UUID reservationId, Instant checkedInAt) {
        data.recordCheckIn(reservationId, checkedInAt);
    }
}
