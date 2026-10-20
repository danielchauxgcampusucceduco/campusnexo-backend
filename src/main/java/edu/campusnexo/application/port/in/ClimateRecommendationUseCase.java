package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.model.ClimateRecommendation;
import edu.campusnexo.domain.policy.ClimateContext;
import java.util.List;

public interface ClimateRecommendationUseCase {
    ClimateRecommendation recommend(String spaceId, ClimateContext context);
    List<ClimateRecommendation> recent(String spaceId, int limit);
}
