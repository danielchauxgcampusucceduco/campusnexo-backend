package edu.campusnexo.infrastructure.persistence.occupancy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOccupancyEstimateRepository extends JpaRepository<OccupancyEstimateEntity, UUID> {
    List<OccupancyEstimateEntity> findBySpaceIdOrderByEstimatedAtDesc(String spaceId, Limit limit);
}
