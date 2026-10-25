package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase.IngestResult;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.infrastructure.adapter.in.web.dto.TelemetryRequest;
import edu.campusnexo.infrastructure.adapter.in.web.dto.TelemetryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BASE)
class TelemetryController {
    private final TelemetryUseCase telemetry;

    TelemetryController(TelemetryUseCase telemetry) {
        this.telemetry = telemetry;
    }

    /** 201 when stored; 200 when the same space and instant had already been stored (idempotent retry). */
    @PostMapping("/telemetry")
    ResponseEntity<TelemetryResponse> ingest(@Valid @RequestBody TelemetryRequest request) {
        IngestResult result = telemetry.ingest(request.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TelemetryResponse.from(result.telemetry()));
    }

    /** Newest first; follow {@code X-Next-Cursor} with {@code ?cursor=} for older pages. */
    @GetMapping("/telemetry")
    ResponseEntity<List<TelemetryResponse>> recent(@RequestParam(required = false) String spaceId,
                                                   @RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit,
                                                   @RequestParam(required = false) String cursor) {
        return PageCursors.page(telemetry.recent(spaceId, limit, PageCursors.decode(cursor)), limit,
                TelemetryUseCase::cursorAfter, TelemetryResponse::from);
    }

    @GetMapping("/spaces/{spaceId}/telemetry")
    ResponseEntity<List<TelemetryResponse>> recentForSpace(@PathVariable String spaceId,
                                                           @RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit,
                                                           @RequestParam(required = false) String cursor) {
        return recent(spaceId, limit, cursor);
    }
}
