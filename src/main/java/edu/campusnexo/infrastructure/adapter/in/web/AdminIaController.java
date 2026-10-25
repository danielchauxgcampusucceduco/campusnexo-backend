package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.AiToolsUseCase;
import edu.campusnexo.application.port.out.AiAdvisorPort.AssignmentPlan;
import edu.campusnexo.application.port.out.AiAdvisorPort.CourseDemand;
import edu.campusnexo.application.port.out.AiAdvisorPort.Forecast;
import edu.campusnexo.application.port.out.AiAdvisorPort.SensorAnomaly;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The AI tools of the administration: assignment of rooms, sensor health, forecast and the evaluation dataset. */
@RestController
@Validated
@RequestMapping(ApiPaths.BASE + "/admin")
class AdminIaController {
    private final AiToolsUseCase tools;

    AdminIaController(AiToolsUseCase tools) {
        this.tools = tools;
    }

    record CursoRequest(@NotBlank @Size(max = 64) String cursoId, @NotNull @Min(1) @Max(5_000) Integer asistentes) {
    }

    record AsignacionRequest(@NotEmpty @Size(max = 18) List<@Valid CursoRequest> cursos,
                             @DecimalMin("1.0") @DecimalMax("2.0") Double margenSeguridad) {
    }

    @PostMapping("/ia/asignacion")
    AssignmentPlan asignacion(@Valid @RequestBody AsignacionRequest request) {
        return tools.asignarSalones(request.cursos().stream()
                        .map(curso -> new CourseDemand(curso.cursoId().strip(), curso.asistentes())).toList(),
                request.margenSeguridad() == null ? 1.10 : request.margenSeguridad());
    }

    @GetMapping("/ia/sensores")
    List<SensorAnomaly> sensores(@RequestParam(defaultValue = "24") @Min(1) @Max(168) int horas) {
        return tools.saludDeSensores(Duration.ofHours(horas));
    }

    @GetMapping("/spaces/{spaceId}/pronostico")
    Forecast pronostico(@PathVariable String spaceId, @RequestParam(defaultValue = "12") int horas) {
        return tools.pronostico(spaceId, horas);
    }

    @GetMapping(value = "/spaces/{spaceId}/historial.csv", produces = "text/csv")
    ResponseEntity<String> historial(@PathVariable String spaceId,
                                     @RequestParam(required = false) Instant desde,
                                     @RequestParam(required = false) Instant hasta) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"historial-" + spaceId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(tools.historialCsv(spaceId, desde, hasta));
    }
}
