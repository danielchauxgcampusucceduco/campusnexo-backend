package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.ConcurrentTwinUpdateException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL adapter of {@link SpaceRepositoryPort}: twin documents and desk regions. */
@Repository
class PostgresSpaceRepositoryAdapter implements SpaceRepositoryPort {
    private final SpringDataSpaceTwinStateRepository states;
    private final SpringDataStudyZoneRoiRepository rois;
    private final JsonMapper json;

    PostgresSpaceRepositoryAdapter(SpringDataSpaceTwinStateRepository states, SpringDataStudyZoneRoiRepository rois,
                                   JsonMapper json) {
        this.states = states;
        this.rois = rois;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredTwin> findStoredTwin(String spaceId) {
        return states.findById(spaceId).map(entity -> new StoredTwin(read(entity), entity.getRevision()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceTwinState> findAllTwins() {
        return states.findAll().stream().map(this::read).toList();
    }

    @Override
    @Transactional
    public StoredTwin saveTwin(SpaceTwinState state, long expectedRevision) {
        String spaceId = state.spaceId();
        String document = json.writeValueAsString(TwinDocument.from(state));
        try {
            SpaceTwinStateEntity entity;
            if (expectedRevision == StoredTwin.NEW) {
                if (states.existsById(spaceId)) {
                    throw new ConcurrentTwinUpdateException(spaceId);
                }
                entity = new SpaceTwinStateEntity(spaceId, state.status(), state.hvacState(), state.updatedAt(), document);
            } else {
                entity = states.findById(spaceId).orElseThrow(() -> new ConcurrentTwinUpdateException(spaceId));
                if (entity.getRevision() != expectedRevision) {
                    throw new ConcurrentTwinUpdateException(spaceId);
                }
                entity.update(state.status(), state.hvacState(), state.updatedAt(), document);
            }
            return new StoredTwin(state, states.saveAndFlush(entity).getRevision());
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException raced) {
            // Another instance updated (or created) the same row between our check and the flush.
            throw new ConcurrentTwinUpdateException(spaceId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyZoneRoi> findRois(String spaceId) {
        return rois.findBySpaceIdOrderByZoneIdAsc(spaceId).stream().map(StudyZoneRoiEntity::toDomain).toList();
    }

    private SpaceTwinState read(SpaceTwinStateEntity entity) {
        return json.readValue(entity.getDocument(), TwinDocument.class).toDomain();
    }
}
