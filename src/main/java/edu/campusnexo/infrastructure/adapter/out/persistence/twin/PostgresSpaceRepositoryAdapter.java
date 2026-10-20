package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.List;
import java.util.Optional;
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
    public Optional<SpaceTwinState> findTwin(String spaceId) {
        return states.findById(spaceId).map(this::read);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceTwinState> findAllTwins() {
        return states.findAll().stream().map(this::read).toList();
    }

    @Override
    @Transactional
    public SpaceTwinState saveTwin(SpaceTwinState state) {
        String document = json.writeValueAsString(TwinDocument.from(state));
        states.save(new SpaceTwinStateEntity(state.spaceId(), state.status(), state.hvacState(), state.updatedAt(), document));
        return state;
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
