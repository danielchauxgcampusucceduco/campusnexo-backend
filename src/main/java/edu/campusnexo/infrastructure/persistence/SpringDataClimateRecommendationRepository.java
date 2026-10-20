package edu.campusnexo.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface SpringDataClimateRecommendationRepository extends JpaRepository<ClimateRecommendationEntity, UUID> {
    List<ClimateRecommendationEntity> findTop100BySpaceIdOrderByGeneratedAtDesc(String spaceId);
    List<ClimateRecommendationEntity> findTop100ByOrderByGeneratedAtDesc();
}
