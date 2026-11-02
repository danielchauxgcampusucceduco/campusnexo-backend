package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.AiToolsUseCase;
import edu.campusnexo.application.port.out.AiAdvisorPort.Forecast;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The occupancy forecast of a room. It only reads the history, so any signed-in role may ask for it, from any network:
 * it is a query, not an administration task (it used to live under /admin and asked for the VPN).
 */
@RestController
@Validated
@RequestMapping(ApiPaths.BASE + "/spaces")
class PronosticoController {
    private final AiToolsUseCase tools;

    PronosticoController(AiToolsUseCase tools) {
        this.tools = tools;
    }

    @GetMapping("/{spaceId}/pronostico")
    Forecast pronostico(@PathVariable String spaceId, @RequestParam(defaultValue = "12") @Min(1) @Max(48) int horas) {
        return tools.pronostico(spaceId, horas);
    }
}
