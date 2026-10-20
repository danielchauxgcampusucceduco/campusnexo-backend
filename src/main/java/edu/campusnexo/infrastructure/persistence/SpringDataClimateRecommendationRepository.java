package edu.campusnexo.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface SpringDataClimateRecommendationRepository extends JpaRepository<ClimateRecommendationEntity, UUID> {}
