package edu.campusnexo.support;

import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryClimateRecommendationRepository implements ClimateRecommendationRepository {
    private final List<ClimateRecommendation> rows = new ArrayList<>();

    @Override
    public ClimateRecommendation save(ClimateRecommendation recommendation) {
        rows.add(recommendation);
        return recommendation;
    }

    @Override
    public Optional<ClimateRecommendation> findById(UUID recommendationId) {
        return rows.stream().filter(row -> row.recommendationId().equals(recommendationId)).findFirst();
    }

    @Override
    public List<ClimateRecommendation> findRecent(String spaceId, ResultLimit limit) {
        return rows.stream().filter(row -> spaceId == null || row.spaceId().equals(spaceId))
                .sorted(Comparator.comparing(ClimateRecommendation::generatedAt).reversed())
                .limit(limit.value()).toList();
    }
}
