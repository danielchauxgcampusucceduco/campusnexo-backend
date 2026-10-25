package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.ClimateRecommendationUseCase;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ClimateRecommendationRequest;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ClimateRecommendationResponse;
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
class ClimateController {
    private final ClimateRecommendationUseCase recommendations;

    ClimateController(ClimateRecommendationUseCase recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping("/spaces/{spaceId}/climate-recommendations")
    ResponseEntity<ClimateRecommendationResponse> recommend(@PathVariable String spaceId,
                                                            @Valid @RequestBody ClimateRecommendationRequest request) {
        ClimateRecommendation recommendation = recommendations.recommend(request.toCommand(spaceId));
        URI location = URI.create(ApiPaths.BASE + "/climate/recommendations/" + recommendation.recommendationId());
        return ResponseEntity.created(location).body(ClimateRecommendationResponse.from(recommendation));
    }

    /** Previous route; prefer {@code POST /api/v1/spaces/{spaceId}/climate-recommendations}. */
    @Deprecated
    @PostMapping("/climate/recommendations")
    ResponseEntity<ClimateRecommendationResponse> recommendLegacy(@RequestParam String spaceId,
                                                                  @Valid @RequestBody ClimateRecommendationRequest request) {
        return recommend(spaceId, request);
    }

    @GetMapping("/climate/recommendations")
    List<ClimateRecommendationResponse> recent(@RequestParam(required = false) String spaceId,
                                               @RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit) {
        return recommendations.recent(spaceId, limit).stream().map(ClimateRecommendationResponse::from).toList();
    }

    @GetMapping("/spaces/{spaceId}/climate-recommendations")
    List<ClimateRecommendationResponse> recentForSpace(@PathVariable String spaceId,
                                                       @RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit) {
        return recommendations.recent(spaceId, limit).stream().map(ClimateRecommendationResponse::from).toList();
    }

    @GetMapping("/climate/recommendations/{recommendationId}")
    ClimateRecommendationResponse get(@PathVariable UUID recommendationId) {
        return ClimateRecommendationResponse.from(recommendations.get(recommendationId));
    }
}
