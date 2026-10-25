package edu.campusnexo.application.catalog;

import edu.campusnexo.application.exception.SpaceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import java.util.List;

public class SpaceCatalogService implements CampusCatalogUseCase {
    private final SpaceCatalogRepository repository;

    public SpaceCatalogService(SpaceCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CampusSpace> listSpaces(SpaceType type) {
        List<CampusSpace> spaces = repository.findAll();
        return type == null ? spaces : spaces.stream().filter(space -> space.type() == type).toList();
    }

    @Override
    public CampusSpace getRequiredSpace(String spaceId) {
        if (spaceId == null || spaceId.isBlank()) {
            throw new SpaceNotFoundException(String.valueOf(spaceId));
        }
        return repository.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
    }
}
