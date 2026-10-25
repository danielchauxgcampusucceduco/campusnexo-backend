package edu.campusnexo.infrastructure.adapter.out.persistence.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCampusSpaceRepository extends JpaRepository<CampusSpaceEntity, String> {
    /** Loads building and branch in the same query (avoids 1 + 2N queries). */
    @EntityGraph(attributePaths = {"building", "building.branch"})
    List<CampusSpaceEntity> findAllByOrderBySpaceIdAsc();

    @EntityGraph(attributePaths = {"building", "building.branch"})
    Optional<CampusSpaceEntity> findWithLocationBySpaceId(String spaceId);
}
