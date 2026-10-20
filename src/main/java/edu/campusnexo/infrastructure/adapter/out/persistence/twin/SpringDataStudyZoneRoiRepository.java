package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataStudyZoneRoiRepository extends JpaRepository<StudyZoneRoiEntity, String> {
    List<StudyZoneRoiEntity> findBySpaceIdOrderByZoneIdAsc(String spaceId);
}
