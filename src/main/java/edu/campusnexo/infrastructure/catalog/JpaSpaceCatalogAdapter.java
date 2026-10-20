package edu.campusnexo.infrastructure.catalog;

import edu.campusnexo.domain.model.CampusSpace;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
class JpaSpaceCatalogAdapter implements SpaceCatalogRepository {
    private final SpringDataCampusSpaceRepository data;
    JpaSpaceCatalogAdapter(SpringDataCampusSpaceRepository data) { this.data = data; }
    @Override @Transactional(readOnly = true)
    public List<CampusSpace> findAll() { return data.findAllByOrderBySpaceIdAsc().stream().map(CampusSpaceEntity::toDomain).toList(); }
    @Override @Transactional(readOnly = true)
    public Optional<CampusSpace> findById(String spaceId) { return data.findBySpaceId(spaceId).map(CampusSpaceEntity::toDomain); }
    @Override public boolean existsById(String spaceId) { return data.existsById(spaceId); }
}
