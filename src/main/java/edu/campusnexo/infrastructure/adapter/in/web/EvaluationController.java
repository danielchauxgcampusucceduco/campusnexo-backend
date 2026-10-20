package edu.campusnexo.infrastructure.web;

import edu.campusnexo.application.port.in.SpaceEvaluationUseCase;
import edu.campusnexo.infrastructure.web.dto.SpaceEvaluationResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BASE)
class EvaluationController {
    private final SpaceEvaluationUseCase evaluation;

    EvaluationController(SpaceEvaluationUseCase evaluation) {
        this.evaluation = evaluation;
    }

    /** Runs the full flow from the latest stored reading: occupancy (AI) → climate recommendation. */
    @PostMapping("/spaces/{spaceId}/evaluations")
    SpaceEvaluationResponse evaluate(@PathVariable String spaceId) {
        return SpaceEvaluationResponse.from(evaluation.evaluate(spaceId));
    }
}
