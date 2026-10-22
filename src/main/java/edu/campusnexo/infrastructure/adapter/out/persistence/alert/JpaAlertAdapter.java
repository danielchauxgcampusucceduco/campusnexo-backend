package edu.campusnexo.infrastructure.adapter.out.persistence.alert;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.AlertRepository;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.domain.shared.ResultLimit;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaAlertAdapter implements AlertRepository {
    /**
     * Transaction-scoped advisory lock on (space, type): concurrent observers, also on other backend instances,
     * take turns between "is there a recent alert?" and the insert, and the lock is released at commit.
     */
    private static final String LOCK_SPACE_AND_TYPE =
            "select 1 from (select pg_advisory_xact_lock(hashtext(?1))) as locked";

    private final SpringDataAlertRepository data;
    private final EntityManager entityManager;

    JpaAlertAdapter(SpringDataAlertRepository data, EntityManager entityManager) {
        this.data = data;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Alert save(Alert alert) {
        return data.save(new AlertEntity(alert)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findRecent(String spaceId, AlertType type, ResultLimit limit, PageCursor before) {
        Limit rows = Limit.of(limit.value());
        List<AlertEntity> entities;
        if (before != null) {
            UUID tieId;
            try {
                tieId = UUID.fromString(before.key());
            } catch (IllegalArgumentException malformed) {
                throw new DomainValidationException("cursor no válido");
            }
            entities = data.findPage(spaceId == null, spaceId == null ? "" : spaceId,
                    type == null ? EnumSet.allOf(AlertType.class) : EnumSet.of(type), before.at(), tieId, rows);
        } else if (spaceId == null && type == null) {
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
    @Transactional
    public boolean saveUnlessRaisedSince(Alert alert, Instant since) {
        entityManager.createNativeQuery(LOCK_SPACE_AND_TYPE)
                .setParameter(1, "alert:" + alert.spaceId() + ":" + alert.type())
                .getSingleResult();
        if (data.existsBySpaceIdAndTypeAndDetectedAtGreaterThanEqual(alert.spaceId(), alert.type(), since)) {
            return false;
        }
        data.save(new AlertEntity(alert));
        return true;
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
