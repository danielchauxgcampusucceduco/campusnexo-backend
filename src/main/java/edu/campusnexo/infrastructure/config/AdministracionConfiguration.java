package edu.campusnexo.infrastructure.config;

import edu.campusnexo.application.activo.InventarioService;
import edu.campusnexo.application.activo.RegistroDePuestosService;
import edu.campusnexo.application.catalog.EspacioAdminService;
import edu.campusnexo.application.ia.AiToolsService;
import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.alert.AlertThresholds;
import edu.campusnexo.application.persona.PersonaService;
import edu.campusnexo.application.port.in.AiToolsUseCase;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.EspacioAdminUseCase;
import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.PersonaAdminUseCase;
import edu.campusnexo.application.port.in.RegistroDePuestosUseCase;
import edu.campusnexo.application.port.out.AiAdvisorPort;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.domain.port.ActivoRepository;
import edu.campusnexo.domain.port.PersonaRepository;
import edu.campusnexo.domain.port.RegistroDePuestosRepository;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.twin.EnergyAccountingPolicy;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of what the university administration manages: people, the workstation inventory and the
 * AI tools it asks through the backend.
 */
@Configuration
class AdministracionConfiguration {

    /** Writes through the catalog proxy, so the cached copy is dropped at once. */
    @Bean
    EspacioAdminUseCase espacioAdmin(SpaceCatalogRepository catalog, ActivoRepository activos) {
        return new EspacioAdminService(catalog, activos);
    }

    @Bean
    PersonaAdminUseCase personaAdmin(PersonaRepository personas) {
        return new PersonaService(personas);
    }

    @Bean
    InventarioUseCase inventario(Clock clock, CampusCatalogUseCase catalog, ActivoRepository activos,
                                 SpaceRepositoryPort twins, AlertRecorder alerts, AlertThresholds thresholds,
                                 @Value("${app.inventario.vigencia-latido}") Duration vigenciaLatido) {
        return new InventarioService(clock, catalog, activos, twins, alerts, vigenciaLatido, thresholds.cooldown());
    }

    @Bean
    RegistroDePuestosUseCase registroDePuestos(Clock clock, CampusCatalogUseCase catalog, ActivoRepository activos,
                                               RegistroDePuestosRepository registro, InventarioUseCase inventario,
                                               @Value("${app.inventario.emparejamiento-anticipacion}") Duration anticipacion,
                                               @Value("${app.inventario.emparejamiento-ranura}") Duration ranura,
                                               @Value("${app.inventario.potencia-cpu-w}") double potenciaCpuW) {
        return new RegistroDePuestosService(clock, catalog, activos, registro, inventario, anticipacion, ranura,
                potenciaCpuW);
    }

    @Bean
    AiToolsUseCase aiTools(Clock clock, CampusCatalogUseCase catalog, TelemetryRepository telemetry,
                           TwinHistoryPort history, AiAdvisorPort advisor, EnergyAccountingPolicy energy,
                           AlertRecorder alerts, AlertThresholds thresholds, TwinProperties properties) {
        return new AiToolsService(clock, catalog, telemetry, history, advisor, energy, alerts, thresholds.cooldown(),
                properties.energy().timeZone());
    }
}
