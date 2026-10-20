package edu.campusnexo.domain.port;

import edu.campusnexo.domain.model.ClimateRecommendation;

public interface ClimateRecommendationRepository {
    ClimateRecommendation save(ClimateRecommendation recommendation);
}
