package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos;
import edu.campusnexo.domain.activo.PropuestaDePuestos.Alta;
import edu.campusnexo.domain.activo.PuestoPropuesto;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Registers the workstations of a room from the camera: the edge device proposes the seats it found in the empty
 * room, each computer is paired with its seat by a pattern on its monitor, and an administrator reviews and
 * confirms. Nothing reaches the inventory without that confirmation.
 */
public interface RegistroDePuestosUseCase {
    /** The edge device's proposal; pairings already made for the same seats are kept. */
    PropuestaDePuestos recibirPropuesta(PropuestaDePuestos propuesta);

    /** The pending proposal with what it would add, what looks incomplete and the computers still unpaired. */
    Revision revisar(String spaceId);

    /** Registers what the proposal adds, except the seats in {@code descartar} (by label, e.g. "A4"). */
    List<Alta> confirmar(String spaceId, Set<String> descartar);

    /** Schedules a slot for every unpaired computer of the room to show its pattern. */
    EmparejamientoCpu iniciarEmparejamiento(String spaceId);

    /** The edge device saw the pattern on the monitor of the seat at that row and column at {@code at}. */
    PropuestaDePuestos patronVisto(String spaceId, int fila, int columna, Instant at);

    /**
     * Heartbeat of a computer agent. A computer already in a seat goes through the inventory; one that is not is
     * recorded as unpaired and told its pattern slot, if a pairing is scheduled.
     */
    Latido latido(String cpu, String spaceId);

    record Revision(PropuestaDePuestos propuesta, List<Alta> altas, List<PuestoPropuesto> incompletos,
                    List<String> cpusSinPuesto, EmparejamientoCpu emparejamiento) {
    }

    /** {@code puesto} is set for a computer in a seat; {@code patron} is its pattern slot while unpaired. */
    record Latido(String cpu, EstadoPuesto puesto, EmparejamientoCpu.Ventana patron) {
    }
}
