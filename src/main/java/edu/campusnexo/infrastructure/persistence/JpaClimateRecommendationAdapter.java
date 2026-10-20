package edu.campusnexo.infrastructure.persistence;

import edu.campusnexo.domain.model.ClimateRecommendation;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import org.springframework.stereotype.Repository;

@Repository
class JpaClimateRecommendationAdapter implements ClimateRecommendationRepository {
    private final SpringDataClimateRecommendationRepository data;
    JpaClimateRecommendationAdapter(SpringDataClimateRecommendationRepository data) { this.data = data; }
    @Override public ClimateRecommendation save(ClimateRecommendation recommendation) {
        data.save(new ClimateRecommendationEntity(recommendation));
        return recommendation;
    }
}
