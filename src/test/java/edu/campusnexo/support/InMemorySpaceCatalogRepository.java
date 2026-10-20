package edu.campusnexo.support;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class InMemorySpaceCatalogRepository implements SpaceCatalogRepository {
    private final List<CampusSpace> spaces;

    public InMemorySpaceCatalogRepository(CampusSpace... spaces) {
        this.spaces = new ArrayList<>(List.of(spaces));
    }

    @Override
    public List<CampusSpace> findAll() {
        return spaces.stream().sorted(Comparator.comparing(CampusSpace::spaceId)).toList();
    }

    @Override
    public Optional<CampusSpace> findById(String spaceId) {
        return spaces.stream().filter(space -> space.spaceId().equals(spaceId)).findFirst();
    }
}
