package edu.campusnexo.domain.port;

import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.shared.ResultLimit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClimateRecommendationRepository {
    ClimateRecommendation save(ClimateRecommendation recommendation);

    Optional<ClimateRecommendation> findById(UUID recommendationId);

    /** Most recent first; {@code spaceId} may be {@code null} for all spaces. */
    List<ClimateRecommendation> findRecent(String spaceId, ResultLimit limit);
}
