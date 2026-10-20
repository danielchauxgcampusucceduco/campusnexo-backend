package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.AlertQueryUseCase;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.infrastructure.adapter.in.web.dto.AlertResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BASE + "/alerts")
class AlertController {
    private final AlertQueryUseCase alerts;

    AlertController(AlertQueryUseCase alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    List<AlertResponse> recent(@RequestParam(required = false) String spaceId,
                               @RequestParam(required = false) AlertType type,
                               @RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit) {
        return alerts.recent(spaceId, type, limit).stream().map(AlertResponse::from).toList();
    }
}
