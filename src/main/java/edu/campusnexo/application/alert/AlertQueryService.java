package edu.campusnexo.application.alert;

import edu.campusnexo.application.port.in.AlertQueryUseCase;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.AlertRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import java.util.List;

public class AlertQueryService implements AlertQueryUseCase {
    private final AlertRepository repository;
    private final CampusCatalogUseCase catalog;

    public AlertQueryService(AlertRepository repository, CampusCatalogUseCase catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Override
    public List<Alert> recent(String spaceId, AlertType type, int limit) {
        ResultLimit resultLimit = ResultLimit.of(limit);
        String filter = spaceId == null || spaceId.isBlank() ? null : spaceId;
        if (filter != null) {
            catalog.getRequiredSpace(filter);
        }
        return repository.findRecent(filter, type, resultLimit);
    }
}
