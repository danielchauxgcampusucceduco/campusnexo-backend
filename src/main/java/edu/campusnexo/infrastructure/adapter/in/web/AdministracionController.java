package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.EspacioAdminUseCase;
import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.PersonaAdminUseCase;
import edu.campusnexo.application.port.in.RegistrarPersonaCommand;
import edu.campusnexo.application.port.in.RegistroDePuestosUseCase;
import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos.Alta;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.domain.shared.DomainValidationException;
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
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

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

    /** Result of a bulk import: rows read, people registered or updated, and each rejected row with its reason. */
    record ImportacionResponse(int filas, int guardadas, List<Rechazo> rechazadas) {
        record Rechazo(int fila, String codigo, String motivo) {
        }
    }

    static final long MAX_ARCHIVO = 2L * 1024 * 1024;

    /**
     * Registers or updates every person of a .csv or .xlsx ({@link PersonasArchivo}); an invalid row is reported with
     * its number and reason without stopping the others. {@code tipo} applies to the rows without a tipo column.
     */
    @PostMapping(path = "/personas/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportacionResponse importarPersonas(@RequestParam("archivo") MultipartFile archivo,
                                         @RequestParam(required = false) TipoPersona tipo) throws IOException {
        if (archivo.isEmpty() || archivo.getSize() > MAX_ARCHIVO) {
            throw new DomainValidationException("El archivo debe tener datos y pesar como máximo 2 MB");
        }
        List<PersonasArchivo.Fila> filas = PersonasArchivo.leer(archivo.getOriginalFilename(), archivo.getBytes());
        int guardadas = 0;
        List<ImportacionResponse.Rechazo> rechazadas = new ArrayList<>();
        for (PersonasArchivo.Fila fila : filas) {
            String codigo = fila.get("codigo");
            try {
                personas.registrar(new RegistrarPersonaCommand(tipoDe(fila.get("tipo"), tipo), codigo,
                        fila.get("nombre"), fila.get("adscripcion")));
                guardadas++;
            } catch (DomainValidationException invalid) {
                rechazadas.add(new ImportacionResponse.Rechazo(fila.numero(), codigo, invalid.getMessage()));
            }
        }
        return new ImportacionResponse(filas.size(), guardadas, rechazadas);
    }

    /** The type written in the row (with its usual synonyms), or the one chosen for the whole file. */
    static TipoPersona tipoDe(String escrito, TipoPersona porDefecto) {
        String tipo = Normalizer.normalize(escrito, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().toLowerCase(Locale.ROOT);
        return switch (tipo) {
            case "docente", "profesor", "profesora", "teacher" -> TipoPersona.DOCENTE;
            case "estudiante", "alumno", "alumna", "student" -> TipoPersona.ESTUDIANTE;
            case "" -> {
                if (porDefecto == null) {
                    throw new DomainValidationException("Falta el tipo: agrega una columna tipo o elige uno al importar");
                }
                yield porDefecto;
            }
            default -> throw new DomainValidationException("Tipo desconocido «" + escrito + "»: usa docente o estudiante");
        };
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
