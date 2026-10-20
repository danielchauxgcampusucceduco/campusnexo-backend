package edu.campusnexo.infrastructure.adapter.out.persistence.climate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataClimateRecommendationRepository extends JpaRepository<ClimateRecommendationEntity, UUID> {
    List<ClimateRecommendationEntity> findBySpaceIdOrderByGeneratedAtDesc(String spaceId, Limit limit);

    List<ClimateRecommendationEntity> findAllByOrderByGeneratedAtDesc(Limit limit);
}
