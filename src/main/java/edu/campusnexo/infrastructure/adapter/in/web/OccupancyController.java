package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.LiveOccupancyUseCase;
import edu.campusnexo.application.port.in.OccupancyEstimationUseCase;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.infrastructure.adapter.in.web.dto.OccupancyEstimateRequest;
import edu.campusnexo.infrastructure.adapter.in.web.dto.OccupancyEstimateResponse;
import edu.campusnexo.infrastructure.adapter.in.web.dto.SpaceOccupancyResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
class OccupancyController {
    private final OccupancyEstimationUseCase estimation;
    private final LiveOccupancyUseCase liveOccupancy;

    OccupancyController(OccupancyEstimationUseCase estimation, LiveOccupancyUseCase liveOccupancy) {
        this.estimation = estimation;
        this.liveOccupancy = liveOccupancy;
    }

    @PostMapping("/spaces/{spaceId}/occupancy-estimates")
    ResponseEntity<OccupancyEstimateResponse> estimate(@PathVariable String spaceId,
                                                       @Valid @RequestBody OccupancyEstimateRequest request) {
        OccupancyRecord record = estimation.estimate(request.toCommand(spaceId));
        URI location = URI.create(ApiPaths.BASE + "/occupancy-estimates/" + record.estimateId());
        return ResponseEntity.created(location).body(OccupancyEstimateResponse.from(record));
    }

    /**
     * Route kept for the current React dashboard. Prefer
     * {@code POST /api/v1/spaces/{spaceId}/occupancy-estimates}.
     */
    @Deprecated
    @PostMapping("/occupancy/estimate")
    OccupancyEstimateResponse estimateLegacy(@RequestParam String spaceId,
                                             @Valid @RequestBody OccupancyEstimateRequest request) {
        return OccupancyEstimateResponse.from(estimation.estimate(request.toCommand(spaceId)));
    }

    @GetMapping("/spaces/{spaceId}/occupancy-estimates")
    List<OccupancyEstimateResponse> recent(@PathVariable String spaceId,
                                           @RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit) {
        return estimation.recent(spaceId, limit).stream().map(OccupancyEstimateResponse::from).toList();
    }

    @GetMapping("/occupancy-estimates/{estimateId}")
    OccupancyEstimateResponse get(@PathVariable UUID estimateId) {
        return OccupancyEstimateResponse.from(estimation.get(estimateId));
    }

    /** Live occupancy and free seats; {@code ?type=BIBLIOTECA} answers "where can I study now?". */
    @GetMapping("/occupancy/live")
    List<SpaceOccupancyResponse> live(@RequestParam(required = false) SpaceType type) {
        return liveOccupancy.current(type).stream().map(SpaceOccupancyResponse::from).toList();
    }
}
