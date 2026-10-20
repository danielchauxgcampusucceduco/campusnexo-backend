package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertType;
import java.util.List;

public interface AlertQueryUseCase {
    List<Alert> recent(String spaceId, AlertType type, int limit);
}
