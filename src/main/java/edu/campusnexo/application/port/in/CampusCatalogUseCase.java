package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.model.CampusSpace;
import java.util.List;

public interface CampusCatalogUseCase {
    List<CampusSpace> listSpaces();
    CampusSpace getRequiredSpace(String spaceId);
}
