package edu.campusnexo.infrastructure.persistence.catalog;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaSpaceCatalogAdapter implements SpaceCatalogRepository {
    private final SpringDataCampusSpaceRepository data;

    JpaSpaceCatalogAdapter(SpringDataCampusSpaceRepository data) {
        this.data = data;
    }

    @Override
    public List<CampusSpace> findAll() {
        return data.findAllByOrderBySpaceIdAsc().stream().map(CampusSpaceEntity::toDomain).toList();
    }

    @Override
    public Optional<CampusSpace> findById(String spaceId) {
        return data.findWithLocationBySpaceId(spaceId).map(CampusSpaceEntity::toDomain);
    }
}
