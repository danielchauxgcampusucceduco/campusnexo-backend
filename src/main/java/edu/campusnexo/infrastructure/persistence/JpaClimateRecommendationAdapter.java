package edu.campusnexo.infrastructure.persistence;

import edu.campusnexo.domain.model.ClimateRecommendation;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
class JpaClimateRecommendationAdapter implements ClimateRecommendationRepository {
    private final SpringDataClimateRecommendationRepository data;
    JpaClimateRecommendationAdapter(SpringDataClimateRecommendationRepository data) { this.data = data; }
    @Override public ClimateRecommendation save(ClimateRecommendation recommendation) {
        data.save(new ClimateRecommendationEntity(recommendation));
        return recommendation;
    }

    @Override
    public List<ClimateRecommendation> findRecent(String spaceId, int limit) {
        List<ClimateRecommendationEntity> results = spaceId == null || spaceId.isBlank()
                ? data.findTop100ByOrderByGeneratedAtDesc()
                : data.findTop100BySpaceIdOrderByGeneratedAtDesc(spaceId);
        return results.stream().limit(limit).map(ClimateRecommendationEntity::toDomain).toList();
    }
}
