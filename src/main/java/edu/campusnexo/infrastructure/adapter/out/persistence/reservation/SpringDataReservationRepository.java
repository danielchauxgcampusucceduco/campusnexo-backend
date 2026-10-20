package edu.campusnexo.infrastructure.adapter.out.persistence.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    @Query("""
            select r from ReservationEntity r
            where r.spaceId = :spaceId and r.startsAt < :endsAt and r.endsAt > :startsAt
            order by r.startsAt
            """)
    List<ReservationEntity> findOverlapping(@Param("spaceId") String spaceId, @Param("startsAt") Instant startsAt,
                                            @Param("endsAt") Instant endsAt);

    Optional<ReservationEntity> findFirstBySpaceIdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
            String spaceId, Instant startsAtOrBefore, Instant endsAfter);

    Optional<ReservationEntity> findFirstBySpaceIdAndStartsAtGreaterThanOrderByStartsAtAsc(String spaceId, Instant after);
}
