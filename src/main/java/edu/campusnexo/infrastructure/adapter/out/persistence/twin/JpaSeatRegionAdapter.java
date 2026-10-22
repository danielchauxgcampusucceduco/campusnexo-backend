package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.domain.port.SeatRegionRepository;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter of {@link SeatRegionRepository}: the desk regions drawn over each camera. */
@Repository
class JpaSeatRegionAdapter implements SeatRegionRepository {
    private final SpringDataStudyZoneRoiRepository rois;

    JpaSeatRegionAdapter(SpringDataStudyZoneRoiRepository rois) {
        this.rois = rois;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyZoneRoi> findRois(String spaceId) {
        return rois.findBySpaceIdOrderByZoneIdAsc(spaceId).stream().map(StudyZoneRoiEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<StudyZoneRoi>> findAllRois() {
        return rois.findAll(Sort.by("spaceId", "zoneId")).stream().map(StudyZoneRoiEntity::toDomain)
                .collect(Collectors.groupingBy(StudyZoneRoi::spaceId, LinkedHashMap::new, Collectors.toList()));
    }
}
