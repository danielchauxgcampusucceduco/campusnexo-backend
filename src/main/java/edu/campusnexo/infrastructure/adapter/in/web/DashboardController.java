package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.DashboardQueryUseCase;
import edu.campusnexo.infrastructure.adapter.in.web.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BASE + "/dashboard")
class DashboardController {
    private final DashboardQueryUseCase dashboard;

    DashboardController(DashboardQueryUseCase dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    DashboardSummaryResponse summary() {
        return DashboardSummaryResponse.from(dashboard.summarizeLast24Hours());
    }
}
