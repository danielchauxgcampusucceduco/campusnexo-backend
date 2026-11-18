package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.DisponibilidadUseCase;
import edu.campusnexo.application.port.in.DisponibilidadUseCase.DisponibilidadDelCampus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Free student spaces hour by hour, for every role: {@code fecha} defaults to today in the campus zone. */
@RestController
@RequestMapping(ApiPaths.BASE + "/disponibilidad")
class DisponibilidadController {
    private final DisponibilidadUseCase disponibilidad;
    private final Clock clock;
    private final ZoneId zone;

    DisponibilidadController(DisponibilidadUseCase disponibilidad, Clock clock,
                             @Value("${app.twin.time-zone}") String timeZone) {
        this.disponibilidad = disponibilidad;
        this.clock = clock;
        this.zone = ZoneId.of(timeZone);
    }

    @GetMapping
    DisponibilidadDelCampus disponibilidad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return disponibilidad.disponibilidad(fecha == null ? LocalDate.now(clock.withZone(zone)) : fecha);
    }
}
