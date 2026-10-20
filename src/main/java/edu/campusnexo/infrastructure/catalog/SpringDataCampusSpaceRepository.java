package edu.campusnexo.infrastructure.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface SpringDataCampusSpaceRepository extends JpaRepository<CampusSpaceEntity, String> {
    List<CampusSpaceEntity> findAllByOrderBySpaceIdAsc();
    Optional<CampusSpaceEntity> findBySpaceId(String spaceId);
}
