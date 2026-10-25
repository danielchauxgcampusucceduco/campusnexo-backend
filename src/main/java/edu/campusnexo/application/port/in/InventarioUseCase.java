package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.activo.TipoActivo;
import java.util.List;

/**
 * Inventory of the workstations of a room (table, chair and computer at each seat) and the heartbeats the
 * computers send while they are on.
 */
public interface InventarioUseCase {
    /** Places an asset at a seat of the room, replacing the one with the same inventory code. */
    void registrarActivo(RegistrarActivoCommand command);

    /** Every workstation of the room with what the camera and the computer report about it now. */
    List<EstadoPuesto> puestos(String spaceId);

    void eliminarActivo(String codigo);

    /**
     * A computer reports it is on. Raises {@code CPU_ENCENDIDA_SIN_USUARIO} when nobody is in the room.
     *
     * @throws edu.campusnexo.application.exception.ResourceNotFoundException if no workstation has that computer
     */
    EstadoPuesto registrarLatido(String codigoCpu);

    /**
     * The camera's view of the furniture at each seat, computed on the edge device (no image leaves the room).
     * Every chair or table the inventory places at a seat and the camera does not see is a finding, and the room
     * gets an {@code ACTIVO_FALTANTE} alert.
     */
    List<Hallazgo> registrarAuditoria(String spaceId, List<ObservacionPuesto> observaciones);

    /** What the camera sees at one seat; {@code zoneId} is the workstation code. */
    record ObservacionPuesto(String zoneId, boolean silla, boolean mesa) {
    }

    /** An asset the inventory places at the workstation and the camera did not see. */
    record Hallazgo(String puesto, TipoActivo tipo, String codigoActivo) {
    }
}
