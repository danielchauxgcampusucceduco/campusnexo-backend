package edu.campusnexo.domain.port;

import edu.campusnexo.domain.model.ClimateRecommendation;
import java.util.List;

public interface ClimateRecommendationRepository {
    ClimateRecommendation save(ClimateRecommendation recommendation);
    List<ClimateRecommendation> findRecent(String spaceId, int limit);
}
