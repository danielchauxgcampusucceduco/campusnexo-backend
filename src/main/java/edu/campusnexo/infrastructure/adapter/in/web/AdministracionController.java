package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.EspacioAdminUseCase;
import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.PersonaAdminUseCase;
import edu.campusnexo.application.port.in.RegistroDePuestosUseCase;
import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos.Alta;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ActivoRequest;
import edu.campusnexo.infrastructure.adapter.in.web.dto.PersonaRequest;
import edu.campusnexo.infrastructure.adapter.in.web.dto.PersonaResponse;
import edu.campusnexo.infrastructure.adapter.in.web.dto.PuestoResponse;
import edu.campusnexo.infrastructure.adapter.in.web.dto.SpaceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What only the university administration manages: the measurements of each room, the people bookings name and
 * the workstation inventory.
 * Every route needs the ADMIN role and, when configured, a request from the administration network.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/admin")
class AdministracionController {
    private final PersonaAdminUseCase personas;
    private final InventarioUseCase inventario;
    private final RegistroDePuestosUseCase registro;
    private final EspacioAdminUseCase espacios;

    AdministracionController(PersonaAdminUseCase personas, InventarioUseCase inventario,
                             RegistroDePuestosUseCase registro, EspacioAdminUseCase espacios) {
        this.personas = personas;
        this.inventario = inventario;
        this.registro = registro;
        this.espacios = espacios;
    }

    /** Measurements taken on site; {@code seatRows} and {@code seatColumns} both null for a room without seats. */
    record EspacioRequest(@NotNull @Min(1) @Max(5_000) Integer capacity,
                          @NotNull @DecimalMin("0.1") @DecimalMax("100000") Double areaSqm,
                          @Min(1) @Max(40) Integer seatRows, @Min(1) @Max(40) Integer seatColumns,
                          @NotNull @Min(0) @Max(1_000) Integer computers, boolean hvacInstalled) {
    }

    @PutMapping("/spaces/{spaceId}")
    SpaceResponse ajustarEspacio(@PathVariable String spaceId, @Valid @RequestBody EspacioRequest request) {
        return SpaceResponse.from(espacios.ajustar(spaceId, new EspacioAdminUseCase.Medidas(request.capacity(),
                request.areaSqm(), request.seatRows(), request.seatColumns(), request.computers(),
                request.hvacInstalled())));
    }

    record ConfirmacionRequest(Set<String> descartar) {
    }

    /** What the camera proposes for the room and what confirming it would add to the inventory. */
    @GetMapping("/spaces/{spaceId}/propuesta")
    RegistroDePuestosUseCase.Revision propuesta(@PathVariable String spaceId) {
        return registro.revisar(spaceId);
    }

    @PostMapping("/spaces/{spaceId}/propuesta/confirmar")
    List<Alta> confirmar(@PathVariable String spaceId, @RequestBody(required = false) ConfirmacionRequest request) {
        return registro.confirmar(spaceId, request == null || request.descartar() == null ? Set.of()
                : request.descartar());
    }

    @PostMapping("/spaces/{spaceId}/emparejamiento")
    EmparejamientoCpu emparejamiento(@PathVariable String spaceId) {
        return registro.iniciarEmparejamiento(spaceId);
    }

    @GetMapping("/personas")
    List<PersonaResponse> personas(@RequestParam(required = false) TipoPersona tipo) {
        return personas.listar(tipo).stream().map(PersonaResponse::from).toList();
    }

    @GetMapping("/personas/{codigo}")
    PersonaResponse persona(@PathVariable String codigo) {
        return PersonaResponse.from(personas.obtener(codigo));
    }

    /** Registers the person or updates the one with the same code. */
    @PostMapping("/personas")
    PersonaResponse registrarPersona(@Valid @RequestBody PersonaRequest request) {
        return PersonaResponse.from(personas.registrar(request.toCommand()));
    }

    @DeleteMapping("/personas/{codigo}")
    ResponseEntity<Void> eliminarPersona(@PathVariable String codigo) {
        personas.eliminar(codigo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/spaces/{spaceId}/puestos")
    List<PuestoResponse> puestos(@PathVariable String spaceId) {
        return inventario.puestos(spaceId).stream().map(PuestoResponse::from).toList();
    }

    @PostMapping("/activos")
    ResponseEntity<Void> registrarActivo(@Valid @RequestBody ActivoRequest request) {
        inventario.registrarActivo(request.toCommand());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/activos/{codigo}")
    ResponseEntity<Void> eliminarActivo(@PathVariable String codigo) {
        inventario.eliminarActivo(codigo);
        return ResponseEntity.noContent().build();
    }
}
