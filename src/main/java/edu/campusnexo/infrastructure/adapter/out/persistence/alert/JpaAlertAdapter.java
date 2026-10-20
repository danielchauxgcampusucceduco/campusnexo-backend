package edu.campusnexo.infrastructure.adapter.out.persistence.alert;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.AlertRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaAlertAdapter implements AlertRepository {
    private final SpringDataAlertRepository data;

    JpaAlertAdapter(SpringDataAlertRepository data) {
        this.data = data;
    }

    @Override
    @Transactional
    public Alert save(Alert alert) {
        return data.save(new AlertEntity(alert)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findRecent(String spaceId, AlertType type, ResultLimit limit) {
        Limit rows = Limit.of(limit.value());
        List<AlertEntity> entities;
        if (spaceId == null && type == null) {
            entities = data.findAllByOrderByDetectedAtDesc(rows);
        } else if (type == null) {
            entities = data.findBySpaceIdOrderByDetectedAtDesc(spaceId, rows);
        } else if (spaceId == null) {
            entities = data.findByTypeOrderByDetectedAtDesc(type, rows);
        } else {
            entities = data.findBySpaceIdAndTypeOrderByDetectedAtDesc(spaceId, type, rows);
        }
        return entities.stream().map(AlertEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsSince(String spaceId, AlertType type, Instant from) {
        return data.existsBySpaceIdAndTypeAndDetectedAtGreaterThanEqual(spaceId, type, from);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<AlertType, Long> countByTypeSince(Instant from) {
        Map<AlertType, Long> counts = new EnumMap<>(AlertType.class);
        for (Object[] row : data.countByTypeSince(from)) {
            counts.put((AlertType) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }
}
