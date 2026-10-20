package edu.campusnexo.application.catalog;

import edu.campusnexo.domain.model.CampusSpace;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import java.util.List;

public class SpaceCatalogService {
    private final SpaceCatalogRepository repository;
    public SpaceCatalogService(SpaceCatalogRepository repository) { this.repository = repository; }
    public List<CampusSpace> list() { return repository.findAll(); }
    public CampusSpace getRequired(String spaceId) {
        return repository.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
    }
}
