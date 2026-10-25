package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.InventarioUseCase;
import edu.campusnexo.application.port.in.RegistroDePuestosUseCase;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.domain.activo.PropuestaDePuestos;
import edu.campusnexo.domain.activo.PuestoPropuesto;
import edu.campusnexo.infrastructure.adapter.in.web.dto.CheckInResponse;
import edu.campusnexo.infrastructure.adapter.in.web.dto.PuestoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Devices of the rooms that are not sensors of the twin: the agent on each computer, which reports it is on,
 * and the kiosk at the door, where the holder of the booking checks in with the institutional code. Both
 * authenticate with a sensor key.
 */
@RestController
@RequestMapping(ApiPaths.BASE)
class DispositivoController {
    private final InventarioUseCase inventario;
    private final ReservationUseCase reservations;
    private final RegistroDePuestosUseCase registro;
    private final Clock clock;

    DispositivoController(InventarioUseCase inventario, ReservationUseCase reservations,
                          RegistroDePuestosUseCase registro, Clock clock) {
        this.inventario = inventario;
        this.reservations = reservations;
        this.registro = registro;
        this.clock = clock;
    }

    /** {@code spaceId} is the room the agent was installed in; it lets a computer not yet in a seat be paired. */
    record LatidoRequest(@NotBlank @Size(max = 40) String cpu, @Size(max = 32) String spaceId) {
    }

    /** {@code puesto} for a computer in a seat; otherwise when to show the pairing pattern, if scheduled. */
    record LatidoResponse(String cpu, boolean asignado, PuestoResponse puesto, Instant patronDesde,
                          Instant patronHasta) {
    }

    record PuestoVistoRequest(@Min(1) @Max(26) int fila, @Min(1) @Max(40) int columna, boolean monitor,
                              boolean silla, boolean mesa) {
    }

    record DescubrimientoRequest(@NotBlank @Size(max = 32) String spaceId, boolean emulado,
                                 @NotNull @Size(min = 1, max = 1_000) List<@Valid PuestoVistoRequest> puestos) {
    }

    record PatronVistoRequest(@NotBlank @Size(max = 32) String spaceId, @Min(1) @Max(26) int fila,
                              @Min(1) @Max(40) int columna, @NotNull Instant at) {
    }

    /** The seats the edge device found in the empty room: a proposal an administrator still has to confirm. */
    @PostMapping("/workstations/discovery")
    ResponseEntity<Void> descubrimiento(@Valid @RequestBody DescubrimientoRequest request) {
        registro.recibirPropuesta(new PropuestaDePuestos(request.spaceId(), clock.instant(), request.emulado(),
                request.puestos().stream()
                        .map(p -> new PuestoPropuesto(p.fila(), p.columna(), p.monitor(), p.silla(), p.mesa(), null))
                        .toList()));
        return ResponseEntity.accepted().build();
    }

    /** The edge device saw the pairing pattern on the monitor of that seat at {@code at}. */
    @PostMapping("/workstations/pairing")
    ResponseEntity<Void> patronVisto(@Valid @RequestBody PatronVistoRequest request) {
        registro.patronVisto(request.spaceId(), request.fila(), request.columna(), request.at());
        return ResponseEntity.accepted().build();
    }

    record CheckInRequest(@NotBlank @Size(max = 32) String codigo) {
    }

    record ObservacionRequest(@NotBlank @Size(max = 48) String zoneId, boolean silla, boolean mesa) {
    }

    record AuditoriaRequest(@NotBlank @Size(max = 32) String spaceId,
                            @NotNull @Size(max = 1_000) List<@Valid ObservacionRequest> puestos) {
    }

    /** The edge device reports the furniture it sees at each seat; the answer lists what the inventory misses. */
    @PostMapping("/workstations/audit")
    List<InventarioUseCase.Hallazgo> auditoria(@Valid @RequestBody AuditoriaRequest request) {
        return inventario.registrarAuditoria(request.spaceId(), request.puestos().stream()
                .map(p -> new InventarioUseCase.ObservacionPuesto(p.zoneId(), p.silla(), p.mesa())).toList());
    }

    @PostMapping("/workstations/heartbeats")
    ResponseEntity<LatidoResponse> latido(@Valid @RequestBody LatidoRequest request) {
        RegistroDePuestosUseCase.Latido latido = registro.latido(request.cpu(), request.spaceId());
        return ResponseEntity.accepted().body(new LatidoResponse(latido.cpu(), latido.puesto() != null,
                latido.puesto() == null ? null : PuestoResponse.from(latido.puesto()),
                latido.patron() == null ? null : latido.patron().desde(),
                latido.patron() == null ? null : latido.patron().hasta()));
    }

    @PostMapping("/spaces/{spaceId}/check-in")
    CheckInResponse checkIn(@PathVariable String spaceId, @Valid @RequestBody CheckInRequest request) {
        return CheckInResponse.from(reservations.checkIn(spaceId, request.codigo()));
    }
}
