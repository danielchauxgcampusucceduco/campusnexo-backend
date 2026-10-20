package edu.campusnexo.application.catalog;

import edu.campusnexo.domain.model.CampusSpace;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import java.util.List;

public class SpaceCatalogService implements CampusCatalogUseCase {
    private final SpaceCatalogRepository repository;
    public SpaceCatalogService(SpaceCatalogRepository repository) { this.repository = repository; }
    @Override public List<CampusSpace> listSpaces() { return repository.findAll(); }
    @Override public CampusSpace getRequiredSpace(String spaceId) {
        return repository.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
    }
}
