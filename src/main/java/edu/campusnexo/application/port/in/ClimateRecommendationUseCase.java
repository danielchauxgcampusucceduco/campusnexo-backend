package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.climate.ClimateRecommendation;
import java.util.List;
import java.util.UUID;

public interface ClimateRecommendationUseCase {
    ClimateRecommendation recommend(RecommendClimateCommand command);

    ClimateRecommendation get(UUID recommendationId);

    List<ClimateRecommendation> recent(String spaceId, int limit);
}
