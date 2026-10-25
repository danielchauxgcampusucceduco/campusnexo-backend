package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.SavingsResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TraceResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TwinSpaceResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TwinStateResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST face of the digital twin: state of every space, energy savings, architecture traces and manual controls. */
@RestController
@RequestMapping(ApiPaths.BASE + "/twin")
class TwinController {
    private final TwinQueryUseCase queries;
    private final TwinControlUseCase controls;

    TwinController(TwinQueryUseCase queries, TwinControlUseCase controls) {
        this.queries = queries;
        this.controls = controls;
    }

    @GetMapping("/spaces")
    List<TwinSpaceResponse> twins() {
        return queries.spaces().stream().map(TwinSpaceResponse::from).toList();
    }

    @GetMapping("/spaces/{spaceId}")
    TwinSpaceResponse twin(@PathVariable String spaceId) {
        return TwinSpaceResponse.from(queries.space(spaceId));
    }

    @GetMapping("/savings")
    SavingsResponse savings() {
        return SavingsResponse.from(queries.savings());
    }

    @GetMapping("/traces")
    List<TraceResponse> traces(@RequestParam(defaultValue = "" + ResultLimit.DEFAULT) int limit) {
        return queries.recentTraces(limit).stream().map(TraceResponse::from).toList();
    }

    /** Duration validated by the use case: between 1 minute and 12 hours. */
    @PostMapping("/spaces/{spaceId}/override")
    TwinStateResponse manualOverride(@PathVariable String spaceId, @RequestParam(defaultValue = "45") long minutes) {
        return TwinStateResponse.from(controls.manualOverride(spaceId, Duration.ofMinutes(minutes)));
    }

    @PostMapping("/spaces/{spaceId}/popup")
    TwinStateResponse openPopUp(@PathVariable String spaceId, @RequestParam(defaultValue = "120") long minutes) {
        return TwinStateResponse.from(controls.openPopUpStudyRoom(spaceId, Duration.ofMinutes(minutes)));
    }

    @PostMapping("/spaces/{spaceId}/maintenance")
    TwinStateResponse maintenance(@PathVariable String spaceId, @RequestParam boolean enabled) {
        return TwinStateResponse.from(controls.setMaintenance(spaceId, enabled));
    }
}
