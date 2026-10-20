package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import java.util.List;

public interface CampusCatalogUseCase {
    /** {@code type} is an optional filter. */
    List<CampusSpace> listSpaces(SpaceType type);

    CampusSpace getRequiredSpace(String spaceId);
}
