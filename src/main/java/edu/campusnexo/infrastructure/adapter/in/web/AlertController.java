package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.AlertQueryUseCase;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.infrastructure.adapter.in.web.dto.AlertResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
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

    /** Newest first; follow {@code X-Next-Cursor} with {@code ?cursor=} for older pages. */
    @GetMapping
    ResponseEntity<List<AlertResponse>> recent(@RequestParam(required = false) String spaceId,
                                               @RequestParam(required = false) AlertType type,
                                               @RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit,
                                               @RequestParam(required = false) String cursor) {
        return PageCursors.page(alerts.recent(spaceId, type, limit, PageCursors.decode(cursor)), limit,
                AlertQueryUseCase::cursorAfter, AlertResponse::from);
    }
}
