package edu.campusnexo.infrastructure.adapter.out.persistence.climate;

import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaClimateRecommendationAdapter implements ClimateRecommendationRepository {
    private final SpringDataClimateRecommendationRepository data;

    JpaClimateRecommendationAdapter(SpringDataClimateRecommendationRepository data) {
        this.data = data;
    }

    @Override
    @Transactional
    public ClimateRecommendation save(ClimateRecommendation recommendation) {
        return data.save(new ClimateRecommendationEntity(recommendation)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClimateRecommendation> findById(UUID recommendationId) {
        return data.findById(recommendationId).map(ClimateRecommendationEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClimateRecommendation> findRecent(String spaceId, ResultLimit limit) {
        Limit rows = Limit.of(limit.value());
        List<ClimateRecommendationEntity> entities = spaceId == null
                ? data.findAllByOrderByGeneratedAtDesc(rows)
                : data.findBySpaceIdOrderByGeneratedAtDesc(spaceId, rows);
        return entities.stream().map(ClimateRecommendationEntity::toDomain).toList();
    }
}
