package edu.campusnexo.domain.port;

import edu.campusnexo.domain.catalog.CampusSpace;
import java.util.List;
import java.util.Optional;

public interface SpaceCatalogRepository {
    List<CampusSpace> findAll();

    Optional<CampusSpace> findById(String spaceId);
}
